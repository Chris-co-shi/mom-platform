package io.github.chrisshi.mom.wms.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.MaterialSnapshot;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountPersistence;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountPersistence.Task;
import io.github.chrisshi.mom.wms.infrastructure.query.InventoryCountQueryMapper;
import io.github.chrisshi.mom.wms.infrastructure.query.InventoryCountQueryMapper.Summary;
import org.springframework.stereotype.Service;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 盘点用例编排；MDM 只读引用在 WMS 本地事务之外验证。 */
@Service
public class InventoryCountApplication {
    private final InventoryCountPersistence store;
    private final InventoryCountQueryMapper queries;
    private final OpeningInventoryReferenceService references;
    private final InventoryCountCommitService commits;
    private final PageAdapter pages;
    private final CurrentActorProvider actors;

    /** @param store 盘点持久化 @param queries 服务端分页 @param references 事务外 MDM 引用
     * @param commits WMS 本地原子提交 @param pages 平台分页转换 @param actors 当前认证操作人 */
    public InventoryCountApplication(InventoryCountPersistence store,InventoryCountQueryMapper queries,
                                     OpeningInventoryReferenceService references,InventoryCountCommitService commits,
                                     PageAdapter pages,CurrentActorProvider actors) {
        this.store=store; this.queries=queries; this.references=references; this.commits=commits; this.pages=pages; this.actors=actors;
    }
    /** 按仓库筛选盘点任务；服务端分页是权威。 */
    public PageResult<Summary> list(PageQuery<Filter> query) {
        Page<Summary> page=pages.toPage(query);
        queries.list(page,query.params().warehouseId());
        return pages.toResult(page);
    }
    /** 读取任务和每条完整维度账面与实盘事实。 */
    public Detail get(String id) {
        Task task=store.find(id);
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        return new Detail(task,store.lines(id).stream().map(line->new LineView(line.id(),line.dimension(),
            line.bookQuantity().toPlainString(),line.countedQuantity()==null?null:line.countedQuantity().toPlainString(),
            line.countedQuantity()==null?null:line.difference().toPlainString(),line.snapshotBy(),line.snapshotAt(),
            line.countedBy(),line.countedAt())).toList());
    }
    /** 只返回当前操作人同键创建的已提交任务；缺失时不能证明原请求未提交。 */
    public Detail findCreatedByRequestKey(String requestKey) {
        if(requestKey==null || requestKey.isBlank() || requestKey.length()>128) throw new IllegalArgumentException("盘点创建请求键非法");
        Task task=store.findByRequestKey(requestKey);
        return task!=null && task.createdBy().equals(actors.requireCurrentActor().actorId()) ? get(task.id()) : null;
    }
    /** 创建单仓盘点草稿，验证仓库及可选范围引用。 */
    public Detail create(CreateRequest request) {
        if (request == null || request.requestKey() == null || request.requestKey().isBlank()
                || request.requestKey().length() > 128) throw new IllegalArgumentException("盘点创建请求键非法");
        // 已提交的同一操作可脱离当前 MDM 可用性重放；范围仍按首次创建时的规范化集合核对。
        Task existing = store.findByRequestKey(request.requestKey());
        if (existing != null) {
            List<String> locations = normalizeScopeIds(request.locationIds());
            List<String> materials = normalizeScopeIds(request.materialIds());
            if (!existing.createdBy().equals(actors.requireCurrentActor().actorId())
                    || !existing.warehouseId().equals(request.warehouseId())
                    || !existing.locationIds().equals(locations) || !existing.materialIds().equals(materials))
                throw new IllegalArgumentException("盘点创建请求键已用于不同内容");
            return get(existing.id());
        }
        var warehouse=references.warehouse(request.warehouseId());
        List<String> locations=validatedLocations(request.locationIds(),warehouse.id());
        List<String> materials=validatedMaterials(request.materialIds());
        String id=commits.create(request.requestKey(),warehouse,locations,materials);
        return get(id);
    }
    /** 更新草稿范围，提交时仍由任务行锁检查状态。 */
    public Detail editScope(String id,ScopeRequest request) {
        Task task=require(id);
        List<String> locations=validatedLocations(request.locationIds(),task.warehouseId());
        List<String> materials=validatedMaterials(request.materialIds());
        commits.editScope(id,locations,materials);
        return get(id);
    }
    /** 重新验证范围后开始盘点并生成账面快照。 */
    public Detail start(String id) {
        Task task=require(id);
        references.warehouse(task.warehouseId());
        validatedLocations(task.locationIds(),task.warehouseId());
        validatedMaterials(task.materialIds());
        commits.start(id,task.version());
        return get(id);
    }
    /** 保存指定完整维度的人工实盘数量。 */
    public Detail count(String id,String lineId,CountRequest request) {
        commits.count(id,lineId,request.quantity()); return get(id);
    }
    /** 校验账外实物的真实 MDM 身份、批次策略及范围，然后创建零账面行。 */
    public Detail addExtra(String id,ExtraRequest request) {
        Task task=require(id);
        MaterialSnapshot material=references.material(request.materialId());
        LocationSnapshot location=references.location(request.locationId(),task.warehouseId());
        if("REQUIRED".equals(material.batchPolicy()) && (request.batchCode()==null || request.batchCode().isBlank()))
            throw new IllegalArgumentException("该物料要求真实批次身份");
        if("NONE".equals(material.batchPolicy()) && request.batchCode()!=null)
            throw new IllegalArgumentException("该物料不启用批次追踪");
        if(!"REQUIRED".equals(material.batchPolicy()) && !"NONE".equals(material.batchPolicy()))
            throw new IllegalArgumentException("物料批次策略未明确");
        if(request.qualityStatus()==null || request.warehouseStatus()==null
                || (request.batchCode()!=null && request.batchCode().isBlank())
                || (request.snCode()!=null && request.snCode().isBlank()))
            throw new IllegalArgumentException("账外实物状态或编码非法");
        commits.addExtra(id,new Dimension(material.id(),material.code(),material.name(),material.uomCode(),
            request.batchCode(),request.snCode(),task.warehouseId(),task.warehouseCode(),location.id(),location.code(),
            request.qualityStatus(),request.warehouseStatus()),request.quantity());
        return get(id);
    }
    /** 确认全部盘点差异；未盘行和超过记账上限的任务拒绝确认。 */
    public Detail confirm(String id) { commits.confirm(id); return get(id); }
    /** 执行整单原子库存调整，重复完成请求返回已提交结果。 */
    public Detail adjust(String id) { commits.adjust(id); return get(id); }
    /** 取消未确认任务，并在同一事务释放持久仓库锁。 */
    public Detail cancel(String id) { commits.cancel(id); return get(id); }
    private Task require(String id) {
        Task task=store.find(id);
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        return task;
    }
    /** 与创建事务保持相同的去重与排序规则，但重放时不重新校验 MDM 实时状态。 */
    private static List<String> normalizeScopeIds(List<String> ids) {
        if (ids != null && ids.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException("盘点范围 ID 非法");
        return ids == null ? List.of() : ids.stream().distinct().sorted().toList();
    }
    private List<String> validatedLocations(List<String> ids,String warehouseId) {
        if(ids!=null && ids.stream().anyMatch(value->value==null || value.isBlank()))
            throw new IllegalArgumentException("盘点范围库位 ID 非法");
        List<String> values=ids==null?List.of():ids.stream().distinct().sorted().toList();
        values.forEach(id->references.location(id,warehouseId)); return values;
    }
    private List<String> validatedMaterials(List<String> ids) {
        if(ids!=null && ids.stream().anyMatch(value->value==null || value.isBlank()))
            throw new IllegalArgumentException("盘点范围物料 ID 非法");
        List<String> values=ids==null?List.of():ids.stream().distinct().sorted().toList();
        values.forEach(references::material); return values;
    }
    /** 可选仓库精确过滤。 */
    public record Filter(String warehouseId) { }
    /** 创建单仓盘点任务的范围。 */
    public record CreateRequest(@NotBlank @Size(max=128) String requestKey,
                                @NotBlank @Size(max=19) String warehouseId,
                                List<@NotBlank @Size(max=19) String> locationIds,
                                List<@NotBlank @Size(max=19) String> materialIds) { }
    /** 更新盘点范围；空集合表示全部。 */
    public record ScopeRequest(List<@NotBlank @Size(max=19) String> locationIds,
                               List<@NotBlank @Size(max=19) String> materialIds) { }
    /** 人工实盘数量；零有效，NULL 禁止。 */
    public record CountRequest(@NotNull @DecimalMin("0") @Digits(integer=18,fraction=6) BigDecimal quantity) { }
    /** 账外实物完整维度，状态由业务人员明确选择。 */
    public record ExtraRequest(@NotBlank @Size(max=19) String materialId,
                               @NotBlank @Size(max=19) String locationId,
                               @Size(max=128) String batchCode,@Size(max=128) String snCode,
                               @NotNull QualityStatus qualityStatus,@NotNull WarehouseStatus warehouseStatus,
                               @NotNull @DecimalMin("0") @Digits(integer=18,fraction=6) BigDecimal quantity) { }
    /** 任务详情及完整维度行。 */
    public record Detail(Task task,List<LineView> lines) { }
    /** 十进制字符串数量展示视图，NULL 实盘表示未盘。 */
    public record LineView(String id,Dimension dimension,String bookQuantity,String countedQuantity,
                           String differenceQuantity,String snapshotBy,Instant snapshotAt,String countedBy,Instant countedAt) { }
}
