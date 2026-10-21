package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.wms.application.InventoryCountModels.Task;
import io.github.chrisshi.mom.wms.application.InventoryCountModels.Line;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountTaskEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountTaskMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountLineMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 盘点任务、持久整仓锁、快照与库存调整的 WMS 本地 REQUIRED 事务入口。 */
@Service
public class InventoryCountCommitService {
    private final InventoryCountTaskMapper tasks;
    private final InventoryAccountingMapper inventory;
    private final InventoryCountLineMapper lineMapper;
    private final InventoryAccountingService accounting;
    private final OpeningDocumentNumberService numbers;
    private final IdGenerator ids;
    private final CurrentActorProvider actors;

    /** @param store WMS 盘点 SQL @param accounting 唯一库存记账入口 @param numbers 现有编码规则
     * @param ids 技术 ID @param actors 可信认证上下文 */
    public InventoryCountCommitService(InventoryCountTaskMapper tasks, InventoryCountLineMapper lineMapper,
                                       InventoryAccountingMapper inventory, InventoryAccountingService accounting,
                                       OpeningDocumentNumberService numbers, IdGenerator ids, CurrentActorProvider actors) {
        this.tasks=tasks; this.lineMapper=lineMapper; this.inventory=inventory; this.accounting=accounting; this.numbers=numbers; this.ids=ids; this.actors=actors;
    }
    private String actor() { return actors.requireCurrentActor().actorId(); }
    private Task require(String id,String state) {
        Task task=InventoryCountModels.task(tasks.lockTask(id));
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        if(!state.equals(task.status())) throw new IllegalStateException("盘点任务状态不允许当前操作");
        return task;
    }
    private void requireOwner(Task task) {
        if(!task.id().equals(inventory.countLockOwner(task.warehouseId())))
            throw new IllegalStateException("盘点任务不持有整仓锁");
    }
    /** 创建任务草稿和正式编号；MDM 仓库已在事务外验证。 */
    @Transactional
    public String create(String requestKey,WarehouseResourceReference warehouse,List<String> locations,List<String> materials) {
        if(requestKey==null || requestKey.isBlank() || requestKey.length()>128)
            throw new IllegalArgumentException("盘点创建请求键非法");
        inventory.lockAccountingKey("WMS:COUNT:CREATE:" + requestKey);
        Task existing=InventoryCountModels.task(tasks.findByRequestKey(requestKey));
        if(existing!=null) {
            if(!existing.warehouseId().equals(warehouse.id()) || !existing.locationIds().equals(locations)
                    || !existing.materialIds().equals(materials) || !existing.createdBy().equals(actor()))
                throw new IllegalArgumentException("盘点创建请求键已用于不同内容");
            return existing.id();
        }
        if(!"ACTIVE".equals(inventory.warehouseInventoryState(warehouse.id())))
            throw new InventoryCountWarehouseInactiveException();
        String id=ids.nextId();
        InventoryCountTaskEntity draft = new InventoryCountTaskEntity();
        draft.setId(id);
        draft.setTaskNo(numbers.allocate("wms.inventory.count"));
        draft.setRequestKey(requestKey);
        draft.setWarehouseId(warehouse.id());
        draft.setWarehouseCode(warehouse.code());
        draft.setLocationIds(locations.toArray(String[]::new));
        draft.setMaterialIds(materials.toArray(String[]::new));
        draft.setCreatedBy(actor());
        draft.setUpdatedBy(actor());
        if (tasks.insert(draft) != 1) throw new IllegalStateException("盘点任务写入失败");
        return id;
    }
    /** 编辑草稿范围；任务行锁串行化并发修改。 */
    @Transactional
    public void editScope(String id,List<String> locations,List<String> materials) {
        require(id,"DRAFT");
        if(tasks.updateScope(id,locations.toArray(String[]::new),materials.toArray(String[]::new),actor())!=1) throw new IllegalStateException("盘点范围更新失败");
    }
    /** 取得仓库事务锁、持久锁后固化完整维度账面快照，三者同事务提交。 */
    @Transactional
    public void start(String id,long expectedVersion) {
        Task task=require(id,"DRAFT");
        if(task.version()!=expectedVersion) throw new IllegalStateException("盘点范围已变化，请刷新");
        inventory.lockAccountingKey("WMS:WH:" + task.warehouseId());
        if(!"ACTIVE".equals(inventory.warehouseInventoryState(task.warehouseId())))
            throw new InventoryCountWarehouseInactiveException();
        if(tasks.acquire(task.warehouseId(),id)!=1) throw new IllegalStateException("仓库已有进行中的盘点任务");
        String actor=actor();
        for (var balance : tasks.books(task.warehouseId(),task.locationIds().toArray(String[]::new),
                task.materialIds().toArray(String[]::new))) {
            InventoryCountLineEntity snapshot = InventoryCountModels.lineRow(
                    ids.nextId(),id,InventoryCountModels.dimension(balance),actor);
            snapshot.setBookQuantity(balance.getQuantity());
            if (lineMapper.insert(snapshot) != 1) throw new IllegalStateException("盘点快照写入失败");
        }
        if(tasks.start(id,"DRAFT",actor,null)!=1)
            throw new IllegalStateException("盘点任务开始失败");
    }
    /** 录入实盘数量；NULL 只表示尚未录入，零是有效实盘。 */
    @Transactional
    public void count(String id,String lineId,BigDecimal quantity) {
        require(id,"COUNTING");
        if(quantity==null || quantity.signum()<0 || quantity.scale()>6 || quantity.precision()-quantity.scale()>18)
            throw new IllegalArgumentException("实盘数量非法");
        if(lineMapper.recordCount(id,lineId,quantity,actor())!=1) throw new IllegalArgumentException("盘点行不存在");
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
        if(dimension.snCode()!=null && inventory.positiveBalanceCountForSn(dimension.snCode()) > 0)
            throw new IllegalStateException("SN 已有正余额，请在对应完整库存维度录入实盘");
        String operator=actor();
        InventoryCountLineEntity extra=InventoryCountModels.lineRow(ids.nextId(),id,dimension,operator);
        extra.setBookQuantity(BigDecimal.ZERO);
        extra.setCountedQuantity(quantity);
        extra.setCountedBy(operator);
        if (lineMapper.insertExtra(extra)!=1) throw new IllegalStateException("账外盘点行写入失败");
    }
    /** 确认所有明细差异；缺失实盘或超出 100 条记账上限时拒绝。 */
    @Transactional
    public void confirm(String id) {
        Task task=require(id,"COUNTING"); requireOwner(task);
        List<Line> lines=lines(id);
        if(lines.stream().anyMatch(line->line.countedQuantity()==null))
            throw new IllegalStateException("仍有未录入实盘数量的库存维度");
        rejectFrozenDifferences(lines);
        if(lines.stream().filter(line->difference(line).signum()!=0).count()>100)
            throw new InventoryCountLimitException();
        if(tasks.confirm(id,"COUNTING",actor(),null)!=1)
            throw new IllegalStateException("盘点差异确认失败");
    }
    /** 将全部差异作为单笔库存交易过账；失败时任务保持 CONFIRMED 且仓库锁有效。 */
    @Transactional
    public String adjust(String id) {
        Task task=InventoryCountModels.task(tasks.lockTask(id));
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        if("COMPLETED".equals(task.status())) return task.transactionId();
        if(!"CONFIRMED".equals(task.status())) throw new IllegalStateException("盘点差异尚未确认");
        requireOwner(task);
        String actor=actor();
        List<Line> lines=lines(id);
        rejectFrozenDifferences(lines);
        List<Entry> entries=lines.stream()
            .filter(line->difference(line).signum()!=0)
            .map(line->new Entry(line.dimension(),difference(line))).toList();
        if(entries.size()>100) throw new InventoryCountLimitException();
        String transactionId=entries.isEmpty()?null:accounting.post(new Posting("INVENTORY_COUNT:"+id,
            "INVENTORY_COUNT_ADJUSTMENT","INVENTORY_COUNT",id,actor,entries));
        if(tasks.complete(id,"CONFIRMED",actor,transactionId)!=1
                || tasks.release(task.warehouseId(),id)!=1)
            throw new IllegalStateException("盘点完成或锁释放失败");
        return transactionId;
    }
    /** 取消未确认任务并释放持久锁；已确认任务必须通过原子调整完成。 */
    @Transactional
    public void cancel(String id) {
        Task task=InventoryCountModels.task(tasks.lockTask(id));
        if(task==null) throw new IllegalArgumentException("盘点任务不存在");
        if("CANCELLED".equals(task.status())) return;
        if(!"DRAFT".equals(task.status()) && !"COUNTING".equals(task.status()))
            throw new IllegalStateException("已确认盘点不可取消");
        if("COUNTING".equals(task.status())) requireOwner(task);
        if(tasks.cancel(id,task.status(),actor(),null)!=1)
            throw new IllegalStateException("盘点取消失败");
        if("COUNTING".equals(task.status()) && tasks.release(task.warehouseId(),id)!=1)
            throw new IllegalStateException("盘点锁释放失败");
    }

    private List<Line> lines(String id) {
        return lineMapper.selectList(Wrappers.<InventoryCountLineEntity>lambdaQuery()
                .eq(InventoryCountLineEntity::getTaskId,id)
                .orderByAsc(InventoryCountLineEntity::getId))
                .stream().map(InventoryCountModels::line).toList();
    }

    /** 盘点差异属于业务计算，不属于 Infrastructure 的行映射职责。 */
    private static java.math.BigDecimal difference(Line line) {
        return line.countedQuantity().subtract(line.bookQuantity());
    }

    private void rejectFrozenDifferences(List<Line> lines) {
        if(lines.stream().anyMatch(line->line.dimension().warehouseStatus()==WarehouseStatus.FROZEN
                && difference(line).signum()!=0)) {
            throw new InventoryCountFrozenDifferenceException();
        }
    }
}
