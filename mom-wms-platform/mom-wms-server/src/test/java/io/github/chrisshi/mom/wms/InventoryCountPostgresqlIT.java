package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.InventoryCountCommitService;
import io.github.chrisshi.mom.wms.application.InventoryCountApplication;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.wms.application.InventoryCountLimitException;
import io.github.chrisshi.mom.wms.application.InventoryCountFrozenDifferenceException;
import io.github.chrisshi.mom.wms.application.InventoryCountWarehouseInactiveException;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication;
import io.github.chrisshi.mom.wms.application.InventoryTransferCommitService;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication.TransferCommand;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.wms.application.InventoryCountModels;
import io.github.chrisshi.mom.wms.application.InventoryCountModels.Task;
import io.github.chrisshi.mom.wms.application.InventoryCountModels.Line;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountTaskMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryCountLineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用真实 PostgreSQL 验证盘点快照、整仓保护、整单调整与失败回滚。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes=MomWmsApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"spring.cloud.nacos.discovery.enabled=false","mom.security.resource-server.enabled=false"})
class InventoryCountPostgresqlIT {
    @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->PG.getJdbcUrl()+"&currentSchema=mom_wms");
        registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
    }
    @Autowired InventoryCountCommitService counts;
    @Autowired InventoryCountApplication application;
    @Autowired InventoryCountTaskMapper taskMapper;
    @Autowired InventoryCountLineMapper lineMapper;
    @Autowired InventoryAccountingService accounting;
    @Autowired InventoryFreezeApplication freezes;
    @Autowired InventoryTransferCommitService transfers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE wms_inventory_count_line,wms_inventory_count_scope_lock,wms_inventory_count_task,"+
            "wms_inventory_unfreeze,wms_inventory_freeze,wms_inventory_transaction_entry,wms_inventory_transaction,"+
            "wms_inventory_balance,wms_warehouse_inventory_state,wms_code_rule_sequence CASCADE");
        jdbc.update("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES ('9000000000000000001','2001','WH-1','ACTIVE')");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("operator-1","secret",List.of()));
    }
    private Task task(String id) {
        return InventoryCountModels.task(taskMapper.selectById(id));
    }

    private List<Line> lines(String taskId) {
        return lineMapper.selectList(Wrappers.<InventoryCountLineEntity>lambdaQuery()
                .eq(InventoryCountLineEntity::getTaskId, taskId)
                .orderByAsc(InventoryCountLineEntity::getId))
                .stream().map(InventoryCountModels::line).toList();
    }

    private WarehouseResourceReference warehouse() {
        return new WarehouseResourceReference("WAREHOUSE","2001","WH-1","主仓","1001","1001",
            "Asia/Shanghai",null,0L,true,null);
    }
    private Dimension dimension(String sn,WarehouseStatus status) {
        return new Dimension("1001","MAT-1","材料一","kg","B1",sn,"2001","WH-1","3001","LOC-1",
            QualityStatus.QUALIFIED,status);
    }
    private void seed(Dimension dimension,String quantity) {
        accounting.post(new Posting("seed-"+dimension.snCode()+"-"+dimension.warehouseStatus(),"RECEIPT","TEST",
            "seed","operator-1",List.of(new Entry(dimension,new BigDecimal(quantity)))));
    }
    @Test void taskListUsesDatabasePaginationAndWarehouseFilter() {
        String id=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of());
        var page=application.list(new PageQuery<>(new InventoryCountApplication.Filter("2001"),1,10));
        assertThat(page.records()).extracting(row->row.id()).containsExactly(id);
        assertThat(page.total()).isEqualTo(1);
        assertThat(application.list(new PageQuery<>(new InventoryCountApplication.Filter("9999"),1,10)).records()).isEmpty();
    }
    @Test void creationRequestKeyReturnsOnlyCurrentActorsCommittedTask() {
        String id=counts.create("count-reconcile",warehouse(),List.of(),List.of());
        assertThat(application.findCreatedByRequestKey("count-reconcile").task().id()).isEqualTo(id);
        assertThat(application.findCreatedByRequestKey("missing-request")).isNull();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("operator-2","secret",List.of()));
        assertThat(application.findCreatedByRequestKey("count-reconcile")).isNull();
    }
    /** 用真实 PostgreSQL 验证 varchar[] 的类型化读写，以及同一请求键的原始范围恢复。 */
    @Test void typedTaskMapperPreservesArrayScopeAndCreationAuditOnReplay() {
        String key = "typed-array-scope";
        List<String> locations = List.of("3001");
        List<String> materials = List.of("1001");
        String id = counts.create(key, warehouse(), locations, materials);
        var persisted = task(id);
        assertThat(persisted.locationIds()).containsExactly("3001");
        assertThat(persisted.materialIds()).containsExactly("1001");
        assertThat(persisted.createdBy()).isEqualTo("operator-1");
        assertThat(persisted.updatedBy()).isEqualTo("operator-1");
        assertThat(persisted.createdAt()).isNotNull();
        assertThat(persisted.updatedAt()).isNotNull();
        assertThat(persisted.version()).isZero();
        assertThat(InventoryCountModels.task(taskMapper.findByRequestKey(key)).locationIds()).containsExactly("3001");
        assertThat(counts.create(key, warehouse(), locations, materials)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_count_task WHERE request_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test void createRejectsWarehouseBeforeInventoryActivation() {
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='INITIALIZING' WHERE warehouse_id='2001'");
        assertThatThrownBy(()->counts.create("inactive-warehouse",warehouse(),List.of(),List.of()))
            .isInstanceOf(InventoryCountWarehouseInactiveException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_count_task",Integer.class)).isZero();
    }
    @Test void draftScopeCanChangeButCannotChangeAfterStart() {
        String id=counts.create("scope-draft",warehouse(),List.of(),List.of());
        counts.editScope(id,List.of("3001"),List.of("1001"));
        assertThat(task(id).locationIds()).containsExactly("3001");
        assertThat(task(id).materialIds()).containsExactly("1001");
        assertThat(task(id).version()).isEqualTo(1);
        counts.start(id,1);
        assertThatThrownBy(()->counts.editScope(id,List.of(),List.of())).isInstanceOf(IllegalStateException.class);
    }
    @Test void snapshotRetainsZeroRowsAndCountAdjustsAtomically() {
        seed(dimension("SN-A",WarehouseStatus.NORMAL),"5");
        seed(dimension("SN-A",WarehouseStatus.FROZEN),"2");
        String id=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of());
        counts.start(id,0);
        var lines=lines(id);
        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(line->line.dimension().warehouseStatus())
            .containsExactlyInAnyOrder(WarehouseStatus.NORMAL,WarehouseStatus.FROZEN);
        assertThatThrownBy(()->accounting.post(new Posting("locked","RECEIPT","TEST","locked","operator-1",
            List.of(new Entry(dimension(null,WarehouseStatus.NORMAL),BigDecimal.ONE)))))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->counts.confirm(id)).isInstanceOf(IllegalStateException.class);
        for(var line:lines) counts.count(id,line.id(),line.dimension().warehouseStatus()==WarehouseStatus.NORMAL
            ?new BigDecimal("4"):new BigDecimal("2"));
        counts.confirm(id);
        assertThatThrownBy(()->jdbc.update("UPDATE wms_inventory_count_line SET counted_quantity=9 WHERE task_id=?",id))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        String transactionId=counts.adjust(id);
        assertThat(transactionId).isNotBlank();
        assertThat(counts.adjust(id)).isEqualTo(transactionId);
        assertThat(task(id).status()).isEqualTo("COMPLETED");
        assertThat(taskMapper.lockOwner("2001")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE source_type='INVENTORY_COUNT' AND source_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance",BigDecimal.class)).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance WHERE created_at IS NULL",Integer.class)).isZero();
    }
    @Test void frozenDifferenceCannotBeConfirmedAndLockRemainsUntilCancellation() {
        seed(dimension("SN-F",WarehouseStatus.FROZEN),"2");
        String id=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of());
        counts.start(id,0);
        counts.count(id,lines(id).getFirst().id(),BigDecimal.ONE);
        assertThatThrownBy(()->counts.confirm(id)).isInstanceOf(InventoryCountFrozenDifferenceException.class);
        assertThat(task(id).status()).isEqualTo("COUNTING");
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(id);
        counts.cancel(id);
        assertThat(taskMapper.lockOwner("2001")).isNull();
    }
    @Test void adjustmentDefensivelyRejectsConfirmedFrozenDifference() {
        seed(dimension("SN-F",WarehouseStatus.FROZEN),"2");
        String id=counts.create("frozen-adjust-guard",warehouse(),List.of(),List.of());
        counts.start(id,0);
        counts.count(id,lines(id).getFirst().id(),BigDecimal.ONE);
        jdbc.update("UPDATE wms_inventory_count_task SET status='CONFIRMED',confirmed_by='operator-1',confirmed_at=now() WHERE id=?",id);
        assertThatThrownBy(()->counts.adjust(id)).isInstanceOf(InventoryCountFrozenDifferenceException.class);
        assertThat(task(id).status()).isEqualTo("CONFIRMED");
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(id);
    }
    @Test void failedAdjustmentKeepsConfirmedTaskAndWarehouseLock() {
        seed(dimension(null,WarehouseStatus.NORMAL),"2");
        String id=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of()); counts.start(id,0);
        counts.count(id,lines(id).getFirst().id(),BigDecimal.ZERO); counts.confirm(id);
        jdbc.update("UPDATE wms_inventory_balance SET quantity=0 WHERE warehouse_id='2001'");
        assertThatThrownBy(()->counts.adjust(id)).isInstanceOf(IllegalStateException.class);
        assertThat(task(id).status()).isEqualTo("CONFIRMED");
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE source_id=?",Integer.class,id)).isZero();
    }
    @Test void cancellationReleasesLockAndOldCreatedAtSurvivesLaterIncrease() {
        Dimension d=dimension(null,WarehouseStatus.NORMAL); seed(d,"2");
        Instant created=jdbc.queryForObject("SELECT created_at FROM wms_inventory_balance",Instant.class);
        String id=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of()); counts.start(id,0);
        counts.cancel(id);
        assertThat(taskMapper.lockOwner("2001")).isNull();
        accounting.post(new Posting("after-cancel","RECEIPT","TEST","after","operator-1",
            List.of(new Entry(d,BigDecimal.ONE))));
        assertThat(jdbc.queryForObject("SELECT created_at FROM wms_inventory_balance",Instant.class)).isEqualTo(created);
    }
    @Test void onlyOneCountCanLockWarehouse() {
        String first=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of());
        String second=counts.create(java.util.UUID.randomUUID().toString(),warehouse(),List.of(),List.of());
        counts.start(first,0);
        assertThatThrownBy(()->counts.start(second,0)).isInstanceOf(IllegalStateException.class);
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(first);
    }
    @Test void createRequestIsIdempotentAndRejectsChangedContent() {
        String id=counts.create("create-key-1",warehouse(),List.of(),List.of());
        assertThat(counts.create("create-key-1",warehouse(),List.of(),List.of())).isEqualTo(id);
        assertThatThrownBy(()->counts.create("create-key-1",warehouse(),List.of("3001"),List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_count_task",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_value FROM wms_code_rule_sequence WHERE rule_id='8300000000000000013'",Long.class)).isEqualTo(1L);
    }
    @Test void zeroBalanceIsSnapshottedAndZeroCountDiffersFromUncounted() {
        seed(dimension(null,WarehouseStatus.NORMAL),"2");
        jdbc.update("UPDATE wms_inventory_balance SET quantity=0");
        String id=counts.create("zero-key",warehouse(),List.of(),List.of()); counts.start(id,0);
        var line=lines(id).getFirst();
        assertThat(line.bookQuantity()).isEqualByComparingTo("0");
        assertThat(line.countedQuantity()).isNull();
        counts.count(id,line.id(),BigDecimal.ZERO);
        assertThat(lines(id).getFirst().countedQuantity()).isEqualByComparingTo("0");
        counts.confirm(id);
        assertThat(counts.adjust(id)).isNull();
        assertThat(task(id).status()).isEqualTo("COMPLETED");
    }
    @Test void freezeUnfreezeAndTransferRejectDuringCount() {
        Dimension d=dimension(null,WarehouseStatus.NORMAL); seed(d,"5");
        String balanceId=jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE warehouse_status='NORMAL'",String.class);
        var frozen=freezes.freezeInventory(new InventoryFreezeApplication.FreezeCommand("freeze-before",balanceId,
            BigDecimal.ONE,InventoryFreezeApplication.FreezeReason.WAREHOUSE_CHECK,"盘前限制","MANUAL","freeze-before","operator-1"));
        String id=counts.create("locked-operations",warehouse(),List.of(),List.of()); counts.start(id,0);
        assertThatThrownBy(()->freezes.freezeInventory(new InventoryFreezeApplication.FreezeCommand("freeze-during",balanceId,
            BigDecimal.ONE,InventoryFreezeApplication.FreezeReason.WAREHOUSE_CHECK,"盘中限制","MANUAL","freeze-during","operator-1")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->freezes.unfreezeInventory(new InventoryFreezeApplication.UnfreezeCommand("unfreeze-during",
            frozen.id(),BigDecimal.ONE,"盘中解冻","MANUAL","unfreeze-during","operator-1")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->transfers.transfer(new TransferCommand("transfer-during",balanceId,"3002",
            BigDecimal.ONE,"operator-1"),"2001",new LocationSnapshot("3002","LOC-2")))
            .isInstanceOf(RuntimeException.class);
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(id);
    }
    @Test void postingCommittedBeforeCountLockIsIncludedInSnapshot() throws Exception {
        String id=counts.create("race-key",warehouse(),List.of(),List.of());
        CountDownLatch posted=new CountDownLatch(1), entering=new CountDownLatch(1), release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var writer=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                accounting.post(new Posting("race-post","RECEIPT","TEST","race","operator-1",
                    List.of(new Entry(dimension(null,WarehouseStatus.NORMAL),new BigDecimal("7")))));
                posted.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("等待盘点启动超时"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertThat(posted.await(5,TimeUnit.SECONDS)).isTrue();
            var counter=pool.submit(()->{
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("operator-1","secret",List.of()));
                entering.countDown();
                counts.start(id,0); return null;
            });
            assertThat(entering.await(5,TimeUnit.SECONDS)).isTrue();
            release.countDown(); writer.get(); counter.get();
        }
        assertThat(lines(id)).hasSize(1);
        assertThat(lines(id).getFirst().bookQuantity()).isEqualByComparingTo("7");
    }
    @Test void countLockCommittedBeforePostingRejectsConcurrentWriter() throws Exception {
        String id=counts.create("count-wins-race",warehouse(),List.of(),List.of());
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var counter=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("operator-1","secret",List.of()));
                counts.start(id,0);
                started.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("等待写入超时"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            var writer=pool.submit(()->accounting.post(new Posting("race-locked","RECEIPT","TEST","race","operator-1",
                List.of(new Entry(dimension(null,WarehouseStatus.NORMAL),BigDecimal.ONE)))));
            release.countDown();
            counter.get();
            assertThatThrownBy(writer::get).hasCauseInstanceOf(IllegalStateException.class);
        }
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE request_key='race-locked'",Integer.class)).isZero();
    }
    @Test void tooManyDifferencesRejectsWholeTaskBeforePosting() {
        String id=counts.create("limit-key",warehouse(),List.of(),List.of()); counts.start(id,0);
        for(int index=0;index<101;index++) {
            Dimension line=new Dimension("1001","MAT-1","材料一","kg","B-"+index,null,
                "2001","WH-1","3001","LOC-1",QualityStatus.QUALIFIED,WarehouseStatus.NORMAL);
            var extra=InventoryCountModels.lineRow(String.format("9%018d",index),id,line,"operator-1");
            extra.setBookQuantity(BigDecimal.ZERO);
            extra.setCountedQuantity(BigDecimal.ONE);
            extra.setCountedBy("operator-1");
            lineMapper.insertExtra(extra);
        }
        assertThatThrownBy(()->counts.confirm(id)).isInstanceOf(InventoryCountLimitException.class);
        assertThat(task(id).status()).isEqualTo("COUNTING");
        assertThat(taskMapper.lockOwner("2001")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction",Integer.class)).isZero();
    }
    @Test void extraPhysicalStockMustBelongToScopeAndDoesNotPostImmediately() {
        String id=counts.create("extra-key",warehouse(),List.of("3001"),List.of("1001")); counts.start(id,0);
        Dimension outside=new Dimension("1002","MAT-2","材料二","kg","B1",null,"2001","WH-1",
            "3001","LOC-1",QualityStatus.QUALIFIED,WarehouseStatus.NORMAL);
        assertThatThrownBy(()->counts.addExtra(id,outside,BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        Dimension inside=dimension(null,WarehouseStatus.NORMAL);
        counts.addExtra(id,inside,BigDecimal.ONE);
        assertThat(lines(id).getFirst().bookQuantity()).isEqualByComparingTo("0");
        assertThatThrownBy(()->counts.addExtra(id,inside,BigDecimal.ONE))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance",Integer.class)).isZero();
        counts.confirm(id);
        counts.adjust(id);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance",BigDecimal.class)).isEqualByComparingTo("1");
    }
}
