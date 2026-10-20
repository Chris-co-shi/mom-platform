package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication.TransferCommand;
import io.github.chrisshi.mom.wms.application.InventoryTransferConflictException;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** 使用真实 PostgreSQL 验证库位移动的守恒、历史、幂等、并发与回滚。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=false"})
class InventoryTransferPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_wms");
        registry.add("spring.flyway.default-schema", () -> "mom_wms");
    }

    @Autowired InventoryAccountingService accounting;
    @Autowired InventoryTransferApplication transfers;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OpeningInventoryReferenceService references;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE wms_inventory_unfreeze, wms_inventory_freeze, wms_inventory_transaction_entry, " +
                "wms_inventory_transaction, wms_inventory_balance, wms_warehouse_inventory_state");
        jdbc.update("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES(?,?,?,'ACTIVE')",
                "9000000000000000001", "2001", "WH-1");
        when(references.location("3002", "2001"))
                .thenReturn(new OpeningInventoryReferenceService.LocationSnapshot("3002", "LOC-2"));
        when(references.location("9999", "2001"))
                .thenThrow(new IllegalArgumentException("目标库位不存在"));
        when(references.location("4001", "2001"))
                .thenThrow(new IllegalArgumentException("目标库位属于其他仓库"));
    }

    private String seed(String sn, String quantity) {
        var dimension = new InventoryAccountingService.Dimension("1001", "MAT-1", "材料一", "kg", "B1", sn,
                "2001", "WH-1", "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(new InventoryAccountingService.Posting("seed-" + sn + "-" + quantity, "RECEIPT", "TEST",
                "seed", "operator-1", List.of(new InventoryAccountingService.Entry(dimension, new BigDecimal(quantity)))));
        return jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE location_id='3001'", String.class);
    }

    private TransferCommand command(String key, String balance, String quantity, String target) {
        return new TransferCommand(key, balance, target, new BigDecimal(quantity), "operator-1");
    }

    @Test void partialAndFullMovePreserveTotalStatesAndTwoImmutableEntries() {
        String balance = seed(null, "500");
        String first = transfers.transfer(command("move-1", balance, "200", "3002")).transactionId();
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance WHERE location_id='3001'", BigDecimal.class))
                .isEqualByComparingTo("300");
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance WHERE location_id='3002'", BigDecimal.class))
                .isEqualByComparingTo("200");
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("500");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry WHERE transaction_id=?",
                Integer.class, first)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT delta_quantity FROM wms_inventory_transaction_entry WHERE transaction_id=? ORDER BY line_no",
                BigDecimal.class, first)).containsExactly(new BigDecimal("-200.000000"), new BigDecimal("200.000000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance WHERE quality_status='QUALIFIED' " +
                "AND warehouse_status='NORMAL' AND uom_code='kg'", Integer.class)).isEqualTo(2);
        transfers.transfer(command("move-2", balance, "300", "3002"));
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance WHERE location_id='3001'", BigDecimal.class))
                .isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance WHERE location_id='3002'", BigDecimal.class))
                .isEqualByComparingTo("500");
    }

    @Test void replaySurvivesLaterStateChangeAndRejectsChangedContent() {
        String balance = seed(null, "10");
        TransferCommand command = command("move-replay", balance, "10", "3002");
        String first = transfers.transfer(command).transactionId();
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='PENDING_ACTIVATION' WHERE warehouse_id='2001'");
        assertThat(transfers.transfer(command).transactionId()).isEqualTo(first);
        assertThatThrownBy(() -> transfers.transfer(command("move-replay", balance, "9", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE transaction_type='LOCATION_TRANSFER'",
                Integer.class)).isEqualTo(1);
    }

    @Test void committedMoveCanBeFoundOnlyByItsOperatorAndOriginalInput() {
        String balance = seed(null, "10");
        assertThat(transfers.findCommitted("move-lookup", "operator-1")).isNull();
        String transactionId = transfers.transfer(command("move-lookup", balance, "2.5", "3002")).transactionId();
        var found = transfers.findCommitted("move-lookup", "operator-1");
        assertThat(found.transactionId()).isEqualTo(transactionId);
        assertThat(found.balanceId()).isEqualTo(balance);
        assertThat(found.targetLocationId()).isEqualTo("3002");
        assertThat(new BigDecimal(found.quantity())).isEqualByComparingTo("2.5");
        assertThat(transfers.findCommitted("move-lookup", "operator-2")).isNull();
    }

    @Test void invalidTargetQuantityAndWarehouseStateLeaveNoMove() {
        String balance = seed(null, "10");
        assertThatThrownBy(() -> transfers.transfer(command("same", balance, "1", "3001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transfers.transfer(command("missing", balance, "1", "9999")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transfers.transfer(command("cross", balance, "1", "4001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transfers.transfer(command("zero", balance, "0", "3002")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transfers.transfer(command("too-much", balance, "11", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='PENDING_ACTIVATION' WHERE warehouse_id='2001'");
        assertThatThrownBy(() -> transfers.transfer(command("inactive", balance, "1", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE transaction_type='LOCATION_TRANSFER'",
                Integer.class)).isZero();
    }

    @Test void snNeedsUniquePositiveBalanceAndFullQuantity() {
        String balance = seed("SN-1", "10");
        assertThatThrownBy(() -> transfers.transfer(command("sn-part", balance, "1", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
        transfers.transfer(command("sn-full", balance, "10", "3002"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance WHERE sn_code='SN-1' AND quantity>0",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT location_id FROM wms_inventory_balance WHERE sn_code='SN-1' AND quantity>0",
                String.class)).isEqualTo("3002");
    }

    @Test void snWithAnotherPositiveStatusBalanceCannotMove() {
        String balance = seed("SN-2", "10");
        var frozen = new InventoryAccountingService.Dimension("1001", "MAT-1", "材料一", "kg", "B1", "SN-2",
                "2001", "WH-1", "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.FROZEN);
        accounting.post(new InventoryAccountingService.Posting("seed-second-sn-status", "RECEIPT", "TEST", "seed",
                "operator-1", List.of(new InventoryAccountingService.Entry(frozen, BigDecimal.ONE))));
        assertThatThrownBy(() -> transfers.transfer(command("sn-multiple", balance, "10", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
    }

    @Test void frozenBalanceCannotUseOrdinaryMove() {
        var frozen = new InventoryAccountingService.Dimension("1001", "MAT-1", "材料一", "kg", "B1", null,
                "2001", "WH-1", "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.FROZEN);
        accounting.post(new InventoryAccountingService.Posting("seed-frozen", "RECEIPT", "TEST", "seed",
                "operator-1", List.of(new InventoryAccountingService.Entry(frozen, new BigDecimal("8")))));
        String balance = jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE warehouse_status='FROZEN'",
                String.class);
        assertThatThrownBy(() -> transfers.transfer(command("move-frozen", balance, "8", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE transaction_type='LOCATION_TRANSFER'",
                Integer.class)).isZero();
    }

    @Test void destinationFailureRollsBackDebitTransactionAndEntry() {
        String balance = seed(null, "10");
        jdbc.update("INSERT INTO wms_inventory_balance(id,material_id,material_code,material_name,uom_code,batch_code," +
                "sn_code,warehouse_id,warehouse_code,location_id,location_code,quality_status,warehouse_status,quantity) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", "9000000000000000099", "1001", "MAT-1", "材料一", "kg",
                "B1", null, "9999", "WH-X", "3002", "LOC-2", "QUALIFIED", "NORMAL", new BigDecimal("4"));
        assertThatThrownBy(() -> transfers.transfer(command("failed-target", balance, "2", "3002")))
                .isInstanceOf(InventoryTransferConflictException.class);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance WHERE id=?", BigDecimal.class, balance))
                .isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE request_key='failed-target'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry e JOIN " +
                "wms_inventory_transaction t ON t.id=e.transaction_id WHERE t.request_key='failed-target'",
                Integer.class)).isZero();
    }

    @Test void concurrentMovesCannotOverdrawSource() throws Exception {
        String balance = seed(null, "100");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("concurrent-a", "concurrent-b").stream().map(key -> pool.submit(() -> {
                ready.countDown(); start.await();
                try { transfers.transfer(command(key, balance, "70", "3002")); return true; }
                catch (InventoryTransferConflictException expected) { return false; }
            })).toList();
            ready.await(); start.countDown();
            assertThat(tasks.stream().filter(task -> {
                try { return task.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE transaction_type='LOCATION_TRANSFER'",
                Integer.class)).isEqualTo(1);
    }

    @Test void concurrentSameKeyReplaysOneTransaction() throws Exception {
        String balance = seed(null, "100");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(1, 2).stream().map(ignored -> pool.submit(() -> {
                ready.countDown(); start.await();
                return transfers.transfer(command("same-parallel", balance, "70", "3002")).transactionId();
            })).toList();
            ready.await(); start.countDown();
            assertThat(tasks.get(0).get()).isEqualTo(tasks.get(1).get());
        }
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance WHERE id=?", BigDecimal.class, balance))
                .isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE request_key='same-parallel'",
                Integer.class)).isEqualTo(1);
    }

    @Test void oppositeDirectionMovesUseStableLockOrder() throws Exception {
        String first = seed(null, "100");
        var other = new InventoryAccountingService.Dimension("1001", "MAT-1", "材料一", "kg", "B1", null,
                "2001", "WH-1", "3002", "LOC-2", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(new InventoryAccountingService.Posting("seed-opposite", "RECEIPT", "TEST", "seed",
                "operator-1", List.of(new InventoryAccountingService.Entry(other, new BigDecimal("100")))));
        String second = jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE location_id='3002'", String.class);
        when(references.location("3001", "2001"))
                .thenReturn(new OpeningInventoryReferenceService.LocationSnapshot("3001", "LOC-1"));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { ready.countDown(); start.await();
                return transfers.transfer(command("opposite-a", first, "30", "3002")); });
            var b = pool.submit(() -> { ready.countDown(); start.await();
                return transfers.transfer(command("opposite-b", second, "40", "3001")); });
            ready.await(); start.countDown();
            assertThat(a.get().transactionId()).isNotBlank();
            assertThat(b.get().transactionId()).isNotBlank();
        }
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("200");
    }
}
