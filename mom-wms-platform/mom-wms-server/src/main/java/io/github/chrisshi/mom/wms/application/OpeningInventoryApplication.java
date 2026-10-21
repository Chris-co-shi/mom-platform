package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.MaterialSnapshot;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryLineMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * WMS 期初库存建账用例编排，负责草稿、明细、过账前 MDM 核验与仓库启用操作。
 * <p>业务事实仅写入 WMS；库存余额及交易由统一记账入口负责。本类不管理采购批次、审批或其他收发作业。</p>
 */
@Service
public class OpeningInventoryApplication {
    private final OpeningInventoryMapper mapper;
    private final InventoryAccountingMapper inventory;
    private final OpeningInventoryLineMapper lineMapper;
    private final OpeningInventoryReferenceService references;
    private final OpeningInventoryCommitService commits;
    private final IdGenerator ids;
    private final CurrentActorProvider actors;
    private final PageAdapter pages;

    /** @param mapper WMS 期初持久化入口 @param references 事务外 MDM 权威引用 @param commits WMS 本地事务入口 @param ids 字符串技术 ID @param actors 当前责任主体 */
    public OpeningInventoryApplication(OpeningInventoryMapper mapper, OpeningInventoryLineMapper lineMapper,
                                       InventoryAccountingMapper inventory, OpeningInventoryReferenceService references,
                                       OpeningInventoryCommitService commits, IdGenerator ids,
                                       CurrentActorProvider actors, PageAdapter pages) {
        this.mapper = mapper; this.lineMapper = lineMapper; this.inventory = inventory; this.references = references; this.commits = commits; this.ids = ids; this.actors = actors;
        this.pages = pages;
    }

    /**
     * 查询期初建账单及其明细；不返回数据库 Entity。
     * @param id 期初单技术 ID @return 单据快照及明细 @throws IllegalArgumentException 单据不存在时抛出
     */
    public Detail get(String id) {
        Document document = requireDocument(id);
        return toView(document, lines(id));
    }

    /**
     * 查询期初单列表，最近创建的单据优先并复用平台分页转换。
     * @param query 分页条件 @return 服务端分页结果
     */
    public PageResult<Document> list(PageQuery<ListFilter> query) {
        Page<OpeningInventoryMapper.DocumentRow> page = pages.toPage(query);
        mapper.list(page, query.params().warehouseId());
        return pages.toResult(page, OpeningInventoryApplication::document);
    }

    /**
     * 创建目标仓库唯一的期初草稿并置为初始化中。MDM 读取发生在 WMS 本地事务之外。
     * @param warehouseId MDM 仓库稳定 ID @return 新建期初单 @throws IllegalArgumentException 仓库无效或重复建账时抛出
     */
    public Detail create(String warehouseId) {
        WarehouseResourceReference warehouse = references.warehouse(warehouseId);
        String documentId = ids.nextId();
        commits.create(documentId, warehouse, actors.requireCurrentActor().actorId());
        return get(documentId);
    }

    /**
     * 新增草稿库存明细；真实批次原样保留，REQUIRED 策略缺少批次时拒绝录入。
     * @param documentId 目标期初单 ID @param input 数量及主数据稳定 ID @return 更新后的单据与明细
     */
    public Detail addLine(String documentId, LineInput input) {
        Document document = requireDocument(documentId);
        MaterialSnapshot material = references.material(input.materialId());
        LocationSnapshot location = references.location(input.locationId(), document.warehouseId());
        validateBatch(material.batchPolicy(), input.batchCode());
        validateInput(input);
        commits.addLine(document, material, location, input, ids.nextId(), actors.requireCurrentActor().actorId());
        return get(documentId);
    }

    /**
     * 编辑草稿明细并重新读取 MDM 快照；已过账单据不可修改。
     * @param documentId 目标期初单 ID @param lineId 目标明细 ID @param input 新明细内容 @return 更新后的单据与明细
     */
    public Detail editLine(String documentId, String lineId, LineInput input) {
        Document document = requireDocument(documentId);
        MaterialSnapshot material = references.material(input.materialId());
        LocationSnapshot location = references.location(input.locationId(), document.warehouseId());
        validateBatch(material.batchPolicy(), input.batchCode());
        validateInput(input);
        commits.editLine(document, lineId, material, location, input, actors.requireCurrentActor().actorId());
        return get(documentId);
    }

    /**
     * 删除草稿明细；已过账单据不可修改。
     * @param documentId 目标期初单 ID @param lineId 目标明细 ID @return 更新后的单据与明细
     */
    public Detail deleteLine(String documentId, String lineId) {
        commits.deleteLine(documentId, lineId, actors.requireCurrentActor().actorId());
        return get(documentId);
    }

    /** 删除未过账草稿及其明细；过账事实不能删除。 */
    /**
     * 删除未过账草稿及其明细；过账事实不能删除。
     * @param documentId 期初单 ID @throws IllegalStateException 已过账单据拒绝删除
     */
    public void deleteDraft(String documentId) { commits.deleteDraft(documentId); }

    /**
     * 校验所有引用后一次性过账；明细上限遵循统一记账入口 100 条。
     * @param documentId 目标期初单 ID @return 已过账单及唯一库存交易 ID
     * @throws IllegalArgumentException 单据为空、数量非法或主数据无效时抛出
     * @throws IllegalStateException 状态或并发版本冲突时抛出；本地写入整体回滚
     */
    public PostedResult post(String documentId) {
        Detail detail = get(documentId);
        if ("POSTED".equals(detail.document().status()))
            return new PostedResult(detail, detail.document().transactionId());
        if (detail.lines().isEmpty() || detail.lines().size() > 100) throw new IllegalArgumentException("期初明细须为 1 至 100 条");
        references.warehouse(detail.document().warehouseId());
        for (LineView line : detail.lines()) {
            MaterialSnapshot material = references.material(line.materialId());
            references.location(line.locationId(), detail.document().warehouseId());
            validateBatch(material.batchPolicy(), line.batchCode());
        }
        String transactionId = commits.post(documentId, detail.document().version(), actors.requireCurrentActor().actorId());
        return new PostedResult(get(documentId), transactionId);
    }

    /**
     * 核对已过账待启用的仓库后显式启用；启用不修改期初库存。
     * @param warehouseId MDM 仓库稳定 ID @return 启用后的 WMS 库存状态
     */
    public String activate(String warehouseId) {
        references.warehouse(warehouseId);
        commits.activate(warehouseId, actors.requireCurrentActor().actorId());
        return inventory.warehouseInventoryState(warehouseId);
    }

    private Document requireDocument(String id) {
        Document value = document(mapper.find(id));
        if (value == null) throw new IllegalArgumentException("期初建账单不存在");
        return value;
    }

    private static void validateBatch(String policy, String batchCode) {
        if ("REQUIRED".equals(policy) && (batchCode == null || batchCode.isBlank()))
            throw new IllegalArgumentException("该物料要求批次，必须录入真实既有批次");
        if ("NONE".equals(policy) && batchCode != null)
            throw new IllegalArgumentException("该物料不启用批次追踪，不能指定批次");
        if (!"REQUIRED".equals(policy) && !"NONE".equals(policy))
            throw new IllegalArgumentException("物料批次策略未明确，不能进行期初建账");
    }

    private static void validateInput(LineInput input) {
        if (input == null || input.quantity() == null || input.quantity().signum() <= 0
                || input.quantity().scale() > 6 || input.qualityStatus() == null || input.warehouseStatus() == null)
            throw new IllegalArgumentException("库存数量或状态非法");
        if (input.batchCode() != null && input.batchCode().isBlank()) throw new IllegalArgumentException("批次编码非法");
        if (input.snCode() != null && input.snCode().isBlank()) throw new IllegalArgumentException("SN 编码非法");
    }

    /**
     * 草稿明细输入；物料/库位名称与单位编码均由后端从 MDM 解析。
     * @param materialId MDM 物料 ID @param locationId MDM 库位 ID @param batchCode 真实批次身份，可选
     * @param snCode 唯一标签，可选 @param qualityStatus 质量状态 @param warehouseStatus 仓储状态 @param quantity 正数库存数量
     */
    public record LineInput(@NotBlank @Size(max = 19) String materialId,
                            @NotBlank @Size(max = 19) String locationId,
                            @Size(max = 128) String batchCode, @Size(max = 128) String snCode,
                            @NotNull QualityStatus qualityStatus, @NotNull WarehouseStatus warehouseStatus,
                            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 18, fraction = 6)
                            BigDecimal quantity) { }
    /** Application 输出契约：不得向 Controller 暴露 Mapper Projection 或数据库 Entity。 */
    public record Document(String id, String documentNo, String warehouseId, String warehouseCode,
                           String status, String transactionId, String createdBy, long version,
                           String warehouseState) { }

    /** 单据及其库存明细查询结果。 @param document 建账单快照 @param lines 建账明细 */
    public record Detail(Document document, List<LineView> lines) { }
    /** 建账单列表可按仓库身份精确筛选，用于创建结果未知时核对。 */
    public record ListFilter(String warehouseId) { }
    /**
     * 期初明细 HTTP 展示视图；数量使用十进制字符串避免浏览器浮点精度损失。
     * @param id 明细 ID @param lineNo 单据内顺序号 @param materialId 物料 ID @param materialCode 物料编码快照
     * @param materialName 物料名称快照 @param batchCode 批次身份 @param snCode 唯一标签 @param warehouseId 仓库 ID
     * @param warehouseCode 仓库编码快照 @param locationId 库位 ID @param locationCode 库位编码快照
     * @param qualityStatus 质量状态 @param warehouseStatus 仓储状态 @param quantity 十进制数量文本 @param uomCode 库存单位快照
     */
    public record LineView(String id, int lineNo, String materialId, String materialCode, String materialName,
                           String batchCode, String snCode, String warehouseId, String warehouseCode,
                           String locationId, String locationCode, QualityStatus qualityStatus,
                           WarehouseStatus warehouseStatus, String quantity, String uomCode) { }
    /** 过账结果及其不可变库存交易关联。 @param detail 已过账单据 @param transactionId 记账交易 ID */
    public record PostedResult(Detail detail, String transactionId) { }

    private static Document document(OpeningInventoryMapper.DocumentRow row) {
        if (row == null) return null;
        return new Document(row.id(), row.documentNo(), row.warehouseId(), row.warehouseCode(),
                row.status(), row.transactionId(), row.createdBy(), row.version(), row.warehouseState());
    }

    private List<OpeningDocumentLineEntity> lines(String documentId) {
        return lineMapper.selectList(Wrappers.<OpeningDocumentLineEntity>lambdaQuery()
                .eq(OpeningDocumentLineEntity::getDocumentId, documentId)
                .orderByAsc(OpeningDocumentLineEntity::getLineNo));
    }

    static Detail toView(Document document, List<OpeningDocumentLineEntity> lines) {
        return new Detail(document, lines.stream().map(line -> new LineView(line.getId(), line.getLineNo(), line.getMaterialId(),
                line.getMaterialCode(), line.getMaterialName(), line.getBatchCode(), line.getSnCode(), line.getWarehouseId(),
                line.getWarehouseCode(), line.getLocationId(), line.getLocationCode(), line.getQualityStatus(),
                line.getWarehouseStatus(), line.getQuantity().toPlainString(), line.getUomCode())).toList());
    }
}
