package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountPersistence;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountPersistence.Task;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountPersistence.Line;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 盘点任务、持久整仓锁、快照与库存调整的 WMS 本地 REQUIRED 事务入口。 */
@Service
public class InventoryCountCommitService {
    private final InventoryCountPersistence store;
    private final InventoryAccountingService accounting;
    private final OpeningDocumentNumberService numbers;
    private final IdGenerator ids;
    private final CurrentActorProvider actors;

    /** @param store WMS 盘点 SQL @param accounting 唯一库存记账入口 @param numbers 现有编码规则
     * @param ids 技术 ID @param actors 可信认证上下文 */
    public InventoryCountCommitService(InventoryCountPersistence store, InventoryAccountingService accounting,
                                       OpeningDocumentNumberService numbers, IdGenerator ids, CurrentActorProvider actors) {
        this.store=store; this.accounting=accounting; this.numbers=numbers; this.ids=ids; this.actors=actors;
    }
    private String actor() { return actors.requireCurrentActor().actorId(); }
    private Task require(String id,String state) {
        Task task=store.lockTask(id);
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        if(!state.equals(task.status())) throw new IllegalStateException("盘点任务状态不允许当前操作");
        return task;
    }
    private void requireOwner(Task task) {
        if(!task.id().equals(store.lockOwner(task.warehouseId())))
            throw new IllegalStateException("盘点任务不持有整仓锁");
    }
    /** 创建任务草稿和正式编号；MDM 仓库已在事务外验证。 */
    @Transactional
    public String create(String requestKey,WarehouseResourceReference warehouse,List<String> locations,List<String> materials) {
        if(requestKey==null || requestKey.isBlank() || requestKey.length()>128)
            throw new IllegalArgumentException("盘点创建请求键非法");
        store.lockCreateRequest(requestKey);
        Task existing=store.findByRequestKey(requestKey);
        if(existing!=null) {
            if(!existing.warehouseId().equals(warehouse.id()) || !existing.locationIds().equals(locations)
                    || !existing.materialIds().equals(materials) || !existing.createdBy().equals(actor()))
                throw new IllegalArgumentException("盘点创建请求键已用于不同内容");
            return existing.id();
        }
        if(!"ACTIVE".equals(store.warehouseState(warehouse.id()))) throw new IllegalStateException("仓库库存未启用");
        String id=ids.nextId();
        store.create(id,numbers.allocate("wms.inventory.count"),requestKey,warehouse.id(),warehouse.code(),locations,materials,actor());
        return id;
    }
    /** 编辑草稿范围；任务行锁串行化并发修改。 */
    @Transactional
    public void editScope(String id,List<String> locations,List<String> materials) {
        require(id,"DRAFT");
        if(store.updateScope(id,locations,materials,actor())!=1) throw new IllegalStateException("盘点范围更新失败");
    }
    /** 取得仓库事务锁、持久锁后固化完整维度账面快照，三者同事务提交。 */
    @Transactional
    public void start(String id,long expectedVersion) {
        Task task=require(id,"DRAFT");
        if(task.version()!=expectedVersion) throw new IllegalStateException("盘点范围已变化，请刷新");
        store.lockWarehouse(task.warehouseId());
        if(!"ACTIVE".equals(store.warehouseState(task.warehouseId()))) throw new IllegalStateException("仓库库存未启用");
        if(store.acquire(task.warehouseId(),id)!=1) throw new IllegalStateException("仓库已有进行中的盘点任务");
        String actor=actor();
        for(var book:store.books(task)) {
            if(store.snapshot(ids.nextId(),id,book,actor)!=1) throw new IllegalStateException("盘点快照写入失败");
        }
        if(store.transition(id,"DRAFT","COUNTING",actor,null)!=1)
            throw new IllegalStateException("盘点任务开始失败");
    }
    /** 录入实盘数量；NULL 只表示尚未录入，零是有效实盘。 */
    @Transactional
    public void count(String id,String lineId,BigDecimal quantity) {
        require(id,"COUNTING");
        if(quantity==null || quantity.signum()<0 || quantity.scale()>6 || quantity.precision()-quantity.scale()>18)
            throw new IllegalArgumentException("实盘数量非法");
        if(store.count(id,lineId,quantity,actor())!=1) throw new IllegalArgumentException("盘点行不存在");
    }
    /** 在任务锁下录入已验证的账外实物，不改变库存余额。 */
    @Transactional
    public void addExtra(String id,InventoryAccountingService.Dimension dimension,BigDecimal quantity) {
        Task task=require(id,"COUNTING");
        if(!task.warehouseId().equals(dimension.warehouseId())
                || (!task.locationIds().isEmpty() && !task.locationIds().contains(dimension.locationId()))
                || (!task.materialIds().isEmpty() && !task.materialIds().contains(dimension.materialId())))
            throw new IllegalArgumentException("账外实物不在盘点范围内");
        if(quantity==null || quantity.signum()<0 || quantity.scale()>6 || quantity.precision()-quantity.scale()>18)
            throw new IllegalArgumentException("账外实物数量非法");
        if(dimension.snCode()!=null && store.snExists(dimension.snCode()))
            throw new IllegalStateException("SN 已有正余额，请在对应完整库存维度录入实盘");
        store.addExtra(ids.nextId(),id,dimension,quantity,actor());
    }
    /** 确认所有明细差异；缺失实盘或超出 100 条记账上限时拒绝。 */
    @Transactional
    public void confirm(String id) {
        Task task=require(id,"COUNTING"); requireOwner(task);
        List<Line> lines=store.lines(id);
        if(lines.stream().anyMatch(line->line.countedQuantity()==null))
            throw new IllegalStateException("仍有未录入实盘数量的库存维度");
        rejectFrozenDifferences(lines);
        if(lines.stream().filter(line->line.difference().signum()!=0).count()>100)
            throw new InventoryCountLimitException();
        if(store.transition(id,"COUNTING","CONFIRMED",actor(),null)!=1)
            throw new IllegalStateException("盘点差异确认失败");
    }
    /** 将全部差异作为单笔库存交易过账；失败时任务保持 CONFIRMED 且仓库锁有效。 */
    @Transactional
    public String adjust(String id) {
        Task task=store.lockTask(id);
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        if("COMPLETED".equals(task.status())) return task.transactionId();
        if(!"CONFIRMED".equals(task.status())) throw new IllegalStateException("盘点差异尚未确认");
        requireOwner(task);
        String actor=actor();
        List<Line> lines=store.lines(id);
        rejectFrozenDifferences(lines);
        List<Entry> entries=lines.stream()
            .filter(line->line.difference().signum()!=0)
            .map(line->new Entry(line.dimension(),line.difference())).toList();
        if(entries.size()>100) throw new InventoryCountLimitException();
        String transactionId=entries.isEmpty()?null:accounting.post(new Posting("INVENTORY_COUNT:"+id,
            "INVENTORY_COUNT_ADJUSTMENT","INVENTORY_COUNT",id,actor,entries));
        if(store.transition(id,"CONFIRMED","COMPLETED",actor,transactionId)!=1
                || store.release(task.warehouseId(),id)!=1)
            throw new IllegalStateException("盘点完成或锁释放失败");
        return transactionId;
    }
    /** 取消未确认任务并释放持久锁；已确认任务必须通过原子调整完成。 */
    @Transactional
    public void cancel(String id) {
        Task task=store.lockTask(id);
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        if("CANCELLED".equals(task.status())) return;
        if(!"DRAFT".equals(task.status()) && !"COUNTING".equals(task.status()))
            throw new IllegalStateException("已确认盘点不可取消");
        if("COUNTING".equals(task.status())) requireOwner(task);
        if(store.transition(id,task.status(),"CANCELLED",actor(),null)!=1)
            throw new IllegalStateException("盘点取消失败");
        if("COUNTING".equals(task.status()) && store.release(task.warehouseId(),id)!=1)
            throw new IllegalStateException("盘点锁释放失败");
    }

    private void rejectFrozenDifferences(List<Line> lines) {
        if(lines.stream().anyMatch(line->line.dimension().warehouseStatus()==WarehouseStatus.FROZEN
                && line.difference().signum()!=0)) {
            throw new InventoryCountFrozenDifferenceException();
        }
    }
}
