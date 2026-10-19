package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.LineInput;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.MaterialSnapshot;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingOperations;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper.Document;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper.Line;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 期初单和库存核心的 WMS 本地原子提交边界。
 * <p>所有写操作使用 PostgreSQL REQUIRED 事务；过账同时写期初状态、仓库生命周期与库存交易/余额/分录。此类不进行 Feign 调用。</p>
 */
@Service
public class OpeningInventoryCommitService {
    private final OpeningInventoryMapper mapper;
    private final InventoryAccountingService accounting;
    private final IdGenerator ids;

    /** @param mapper 期初单持久化入口 @param accounting 唯一库存记账服务 @param ids 技术主键生成器 */
    public OpeningInventoryCommitService(OpeningInventoryMapper mapper, InventoryAccountingService accounting,
                                         IdGenerator ids) {
        this.mapper = mapper; this.accounting = accounting; this.ids = ids;
    }

    /**
     * 创建唯一仓库期初单并原子进入 INITIALIZING。
     * @param id 期初单技术 ID @param documentNo 期初单业务编号 @param warehouse 经 MDM 验证的仓库快照
     * @param actor 当前责任主体 @throws IllegalStateException 仓库已开始建账或已启用时整体回滚
     */
    @Transactional
    public void create(String id, String documentNo, WarehouseResourceReference warehouse, String actor) {
        mapper.initialize(ids.nextId(), warehouse.id(), warehouse.code());
        String state = mapper.lockWarehouse(warehouse.id());
        if (!"INITIALIZING".equals(state)) throw new IllegalStateException("仓库已过账待启用或已启用，不可再次进行期初建账");
        if (mapper.hasWarehouseBalance(warehouse.id()))
            throw new IllegalStateException("该仓库已存在实时余额，不能通过期初单重复增加初始库存");
        if (mapper.createDocument(id, documentNo, warehouse.id(), warehouse.code(), actor) != 1)
            throw new IllegalStateException("期初草稿写入失败");
    }

    /**
     * 新增草稿行；并发修改由单据行锁串行化。
     * @param requested 事务外读取的单据快照 @param material 经 MDM 验证的物料/UOM 快照
     * @param location 经 MDM 验证的库位快照 @param input 明细输入 @param lineId 新明细技术 ID
     */
    @Transactional
    public void addLine(Document requested, MaterialSnapshot material, LocationSnapshot location,
                        LineInput input, String lineId) {
        Document document = requireDraft(requested.id());
        if (!document.warehouseId().equals(requested.warehouseId())) throw new IllegalStateException("建账单仓库已变化");
        insert(document, material, location, input, lineId);
        bumpVersion(document.id());
    }

    /**
     * 更新草稿行并刷新可信快照。
     * @param requested 已读取单据快照 @param lineId 目标明细 ID @param material 当前物料快照
     * @param location 当前库位快照 @param input 新明细输入 @throws IllegalStateException 状态/版本冲突时回滚
     */
    @Transactional
    public void editLine(Document requested, String lineId, MaterialSnapshot material, LocationSnapshot location,
                         LineInput input) {
        Document document = requireDraft(requested.id());
        if (!document.warehouseId().equals(requested.warehouseId())) throw new IllegalStateException("建账单仓库已变化");
        if (mapper.updateLine(document.id(), lineId, material.id(), material.code(), material.name(), input.batchCode(),
                input.snCode(), location.id(), location.code(), input.qualityStatus(), input.warehouseStatus(),
                input.quantity(), material.uomCode()) != 1) throw new IllegalArgumentException("期初明细不存在");
        bumpVersion(document.id());
    }

    /**
     * 删除草稿行；已过账状态拒绝删除。
     * @param documentId 期初单 ID @param lineId 明细 ID @throws IllegalStateException 已过账时整体回滚
     */
    @Transactional
    public void deleteLine(String documentId, String lineId) {
        requireDraft(documentId);
        if (mapper.deleteLine(documentId, lineId) != 1) throw new IllegalArgumentException("期初明细不存在");
        bumpVersion(documentId);
    }

    /**
     * 删除未过账建账单及明细并清理其初始化状态；已过账数据保留为不可变业务事实。
     * @param documentId 期初单 ID @throws IllegalStateException 单据已过账时拒绝删除
     */
    @Transactional
    public void deleteDraft(String documentId) {
        Document document = requireDraft(documentId);
        mapper.deleteLinesByDocument(documentId);
        if (mapper.deleteDraft(documentId) != 1) throw new IllegalStateException("期初单状态发生并发冲突");
        if (mapper.deleteInitializingWarehouse(document.warehouseId()) != 1)
            throw new IllegalStateException("仓库初始化状态发生并发冲突");
    }

    /**
     * 在同一 PostgreSQL REQUIRED 事务内一次过账全部分录。
     * 同单并发由 FOR UPDATE 串行化，状态更新和 Inventory request_key 唯一约束共同保证幂等；任一明细失败全部回滚。
     */
    @Transactional
    public String post(String documentId, long expectedVersion, String actor) {
        Document document = mapper.lockDocument(documentId);
        if (document == null) throw new IllegalArgumentException("期初建账单不存在");
        if ("POSTED".equals(document.status())) return document.transactionId();
        if (!"DRAFT".equals(document.status())) throw new IllegalStateException("期初建账单状态不可过账");
        if (document.version() != expectedVersion) throw new IllegalStateException("期初明细在校验后发生变化，请刷新后重新确认");
        if (!"INITIALIZING".equals(mapper.lockWarehouse(document.warehouseId())))
            throw new IllegalStateException("仓库不处于期初初始化状态");
        List<Line> lines = mapper.lines(documentId);
        if (lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("期初明细须为 1 至 100 条");
        List<Entry> entries = lines.stream().map(line -> new Entry(new Dimension(line.materialId(), line.materialCode(),
                line.materialName(), line.uomCode(), line.batchCode(), line.snCode(), line.warehouseId(),
                line.warehouseCode(), line.locationId(), line.locationCode(), line.qualityStatus(),
                line.warehouseStatus()), line.quantity())).toList();
        String transactionId = accounting.post(new Posting("OPENING:" + documentId, "OPENING", "OPENING_DOCUMENT",
                documentId, actor, entries));
        if (mapper.postDocument(documentId, transactionId, expectedVersion) != 1
                || mapper.markPending(document.warehouseId(), documentId) != 1)
            throw new IllegalStateException("期初单或仓库状态发生并发冲突");
        return transactionId;
    }

    /**
     * 核对期初已过账且未重复启用后，将仓库状态切换为 ACTIVE。
     * @param warehouseId WMS 仓库状态 ID @param actor 实际启用操作人 @throws IllegalStateException 状态不是待启用时抛出
     */
    @Transactional
    public void activate(String warehouseId, String actor) {
        String state = mapper.lockWarehouse(warehouseId);
        if (!"PENDING_ACTIVATION".equals(state)) throw new IllegalStateException("仓库必须处于已过账待启用状态");
        if (mapper.activate(warehouseId, actor) != 1) throw new IllegalStateException("仓库启用状态发生并发冲突");
    }

    private void insert(Document document, MaterialSnapshot material, LocationSnapshot location,
                        LineInput input, String lineId) {
        if (mapper.insertLine(lineId, document.id(), mapper.nextLineNo(document.id()), material.id(), material.code(),
                material.name(), input.batchCode(), input.snCode(), document.warehouseId(), document.warehouseCode(),
                location.id(), location.code(), input.qualityStatus(), input.warehouseStatus(), input.quantity(),
                material.uomCode()) != 1) throw new IllegalStateException("期初明细写入失败");
    }

    private void bumpVersion(String documentId) {
        if (mapper.bumpDocumentVersion(documentId) != 1) throw new IllegalStateException("期初单版本更新发生并发冲突");
    }

    private Document requireDraft(String id) {
        Document document = mapper.lockDocument(id);
        if (document == null) throw new IllegalArgumentException("期初建账单不存在");
        if (!"DRAFT".equals(document.status())) throw new IllegalStateException("已过账建账单不可修改");
        return document;
    }

    private OpeningInventoryApplication.Detail detail(String id) {
        Document document = mapper.find(id);
        return OpeningInventoryApplication.toView(document, mapper.lines(id));
    }
}
