package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.LineInput;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.MaterialSnapshot;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentEntity;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryLineMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.Document;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper.DocumentRow;
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
    private final OpeningInventoryLineMapper lineMapper;
    private final InventoryAccountingService accounting;
    private final IdGenerator ids;
    private final OpeningDocumentNumberService numbers;

    /** @param mapper 期初单持久化入口 @param accounting 唯一库存记账服务 @param ids 技术主键生成器 @param numbers WMS 正式单据发号入口 */
    public OpeningInventoryCommitService(OpeningInventoryMapper mapper, OpeningInventoryLineMapper lineMapper,
                                         InventoryAccountingService accounting,
                                         IdGenerator ids, OpeningDocumentNumberService numbers) {
        this.mapper = mapper; this.lineMapper = lineMapper; this.accounting = accounting; this.ids = ids; this.numbers = numbers;
    }

    /**
     * 创建唯一仓库期初单并原子进入 INITIALIZING。
     * @param id 期初单技术 ID @param warehouse 经 MDM 验证的仓库快照
     * @param actor 当前责任主体 @throws IllegalStateException 仓库已开始建账或已启用时整体回滚
     */
    @Transactional
    public void create(String id, WarehouseResourceReference warehouse, String actor) {
        mapper.initialize(ids.nextId(), warehouse.id(), warehouse.code(), actor);
        String state = mapper.lockWarehouse(warehouse.id());
        if (!"INITIALIZING".equals(state)) throw new IllegalStateException("仓库已过账待启用或已启用，不可再次进行期初建账");
        if (mapper.hasWarehouseBalance(warehouse.id()))
            throw new IllegalStateException("该仓库已存在实时余额，不能通过期初单重复增加初始库存");
        String documentNo = numbers.allocate();
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
                        LineInput input, String lineId, String actor) {
        DocumentRow document = requireDraft(requested.id());
        if (!document.warehouseId().equals(requested.warehouseId())) throw new IllegalStateException("建账单仓库已变化");
        insert(document, material, location, input, lineId);
        bumpVersion(document.id(), actor);
    }

    /**
     * 更新草稿行并刷新可信快照。
     * @param requested 已读取单据快照 @param lineId 目标明细 ID @param material 当前物料快照
     * @param location 当前库位快照 @param input 新明细输入 @throws IllegalStateException 状态/版本冲突时回滚
     */
    @Transactional
    public void editLine(Document requested, String lineId, MaterialSnapshot material, LocationSnapshot location,
                         LineInput input, String actor) {
        DocumentRow document = requireDraft(requested.id());
        if (!document.warehouseId().equals(requested.warehouseId())) throw new IllegalStateException("建账单仓库已变化");
        // 使用 mom-data 的 update(entity, wrapper)，同时保持 id + documentId 归属条件。
        // batch_code、sn_code 可以清空；必须显式 wrapper.set(null)，不能依赖默认 NOT_NULL 字段策略。
        OpeningDocumentLineEntity changes = new OpeningDocumentLineEntity();
        changes.setMaterialId(material.id());
        changes.setMaterialCode(material.code());
        changes.setMaterialName(material.name());
        changes.setLocationId(location.id());
        changes.setLocationCode(location.code());
        changes.setQualityStatus(input.qualityStatus());
        changes.setWarehouseStatus(input.warehouseStatus());
        changes.setQuantity(input.quantity());
        changes.setUomCode(material.uomCode());
        var scopedUpdate = Wrappers.<OpeningDocumentLineEntity>lambdaUpdate()
                .eq(OpeningDocumentLineEntity::getId, lineId)
                .eq(OpeningDocumentLineEntity::getDocumentId, document.id())
                .set(OpeningDocumentLineEntity::getBatchCode, input.batchCode())
                .set(OpeningDocumentLineEntity::getSnCode, input.snCode());
        if (lineMapper.update(changes, scopedUpdate) != 1)
            throw new IllegalArgumentException("期初明细不存在");
        bumpVersion(document.id(), actor);
    }

    /**
     * 删除草稿行；已过账状态拒绝删除。
     * @param documentId 期初单 ID @param lineId 明细 ID @throws IllegalStateException 已过账时整体回滚
     */
    @Transactional
    public void deleteLine(String documentId, String lineId, String actor) {
        requireDraft(documentId);
        if (lineMapper.delete(Wrappers.<OpeningDocumentLineEntity>lambdaQuery()
                .eq(OpeningDocumentLineEntity::getId, lineId)
                .eq(OpeningDocumentLineEntity::getDocumentId, documentId)) != 1)
            throw new IllegalArgumentException("期初明细不存在");
        bumpVersion(documentId, actor);
    }

    /**
     * 删除未过账建账单及明细并清理其初始化状态；已过账数据保留为不可变业务事实。
     * @param documentId 期初单 ID @throws IllegalStateException 单据已过账时拒绝删除
     */
    @Transactional
    public void deleteDraft(String documentId) {
        DocumentRow document = requireDraft(documentId);
        lineMapper.delete(Wrappers.<OpeningDocumentLineEntity>lambdaQuery()
                .eq(OpeningDocumentLineEntity::getDocumentId, documentId));
        if (mapper.delete(Wrappers.<OpeningDocumentEntity>lambdaQuery()
                .eq(OpeningDocumentEntity::getId, documentId)
                .eq(OpeningDocumentEntity::getStatus, "DRAFT")) != 1) throw new IllegalStateException("期初单状态发生并发冲突");
        if (mapper.deleteInitializingWarehouse(document.warehouseId()) != 1)
            throw new IllegalStateException("仓库初始化状态发生并发冲突");
    }

    /**
     * 在同一 PostgreSQL REQUIRED 事务内一次过账全部分录。
     * 同单并发由 FOR UPDATE 串行化，状态更新和 Inventory request_key 唯一约束共同保证幂等；任一明细失败全部回滚。
     */
    @Transactional
    public String post(String documentId, long expectedVersion, String actor) {
        DocumentRow document = mapper.lockDocument(documentId);
        if (document == null) throw new IllegalArgumentException("期初建账单不存在");
        if ("POSTED".equals(document.status())) return document.transactionId();
        if (!"DRAFT".equals(document.status())) throw new IllegalStateException("期初建账单状态不可过账");
        if (document.version() != expectedVersion) throw new IllegalStateException("期初明细在校验后发生变化，请刷新后重新确认");
        if (!"INITIALIZING".equals(mapper.lockWarehouse(document.warehouseId())))
            throw new IllegalStateException("仓库不处于期初初始化状态");
        List<OpeningDocumentLineEntity> lines = lines(documentId);
        if (lines.isEmpty() || lines.size() > 100) throw new IllegalArgumentException("期初明细须为 1 至 100 条");
        List<Entry> entries = lines.stream().map(line -> new Entry(new Dimension(line.getMaterialId(), line.getMaterialCode(),
                line.getMaterialName(), line.getUomCode(), line.getBatchCode(), line.getSnCode(), line.getWarehouseId(),
                line.getWarehouseCode(), line.getLocationId(), line.getLocationCode(), line.getQualityStatus(),
                line.getWarehouseStatus()), line.getQuantity())).toList();
        String transactionId = accounting.post(new Posting("OPENING:" + documentId, "OPENING", "OPENING_DOCUMENT",
                documentId, actor, entries));
        if (mapper.postDocument(documentId, transactionId, expectedVersion, actor) != 1
                || mapper.markPending(document.warehouseId(), documentId, actor) != 1)
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

    private void insert(DocumentRow document, MaterialSnapshot material, LocationSnapshot location,
                         LineInput input, String lineId) {
        OpeningDocumentLineEntity row = line(document, material, location, input, lineId);
        row.setLineNo(lineMapper.nextLineNo(document.id()));
        if (lineMapper.insert(row) != 1) throw new IllegalStateException("期初明细写入失败");
    }

    /** 类型化行装配属于 Application；Mapper 不解释物料、库位或生命周期业务规则。 */
    private static OpeningDocumentLineEntity line(DocumentRow document, MaterialSnapshot material,
                                                  LocationSnapshot location, LineInput input, String lineId) {
        OpeningDocumentLineEntity row = new OpeningDocumentLineEntity();
        row.setId(lineId);
        row.setDocumentId(document.id());
        row.setMaterialId(material.id());
        row.setMaterialCode(material.code());
        row.setMaterialName(material.name());
        row.setBatchCode(input.batchCode());
        row.setSnCode(input.snCode());
        row.setWarehouseId(document.warehouseId());
        row.setWarehouseCode(document.warehouseCode());
        row.setLocationId(location.id());
        row.setLocationCode(location.code());
        row.setQualityStatus(input.qualityStatus());
        row.setWarehouseStatus(input.warehouseStatus());
        row.setQuantity(input.quantity());
        row.setUomCode(material.uomCode());
        return row;
    }

    private List<OpeningDocumentLineEntity> lines(String documentId) {
        return lineMapper.selectList(Wrappers.<OpeningDocumentLineEntity>lambdaQuery()
                .eq(OpeningDocumentLineEntity::getDocumentId, documentId)
                .orderByAsc(OpeningDocumentLineEntity::getLineNo));
    }

    private void bumpVersion(String documentId, String actor) {
        if (mapper.bumpDocumentVersion(documentId, actor) != 1)
            throw new IllegalStateException("期初单版本更新发生并发冲突");
    }

    private DocumentRow requireDraft(String id) {
        DocumentRow document = mapper.lockDocument(id);
        if (document == null) throw new IllegalArgumentException("期初建账单不存在");
        if (!"DRAFT".equals(document.status())) throw new IllegalStateException("已过账建账单不可修改");
        return document;
    }

}
