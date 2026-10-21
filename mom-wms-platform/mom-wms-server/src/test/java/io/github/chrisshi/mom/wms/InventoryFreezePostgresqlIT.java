package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import io.github.chrisshi.mom.core.page.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用真实 PostgreSQL 验证冻结限制、余额过账、事务回滚和并发解冻。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=false"})
class InventoryFreezePostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_wms");
        registry.add("spring.flyway.default-schema", () -> "mom_wms");
    }

    @Autowired InventoryAccountingService accounting;
    @Autowired InventoryFreezeApplication freezes;
    @Autowired InventoryQueryService queries;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE wms_inventory_unfreeze, wms_inventory_freeze, wms_inventory_transaction_entry, " +
                "wms_inventory_transaction, wms_inventory_balance, wms_warehouse_inventory_state");
        jdbc.update("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES(?,?,?,'ACTIVE')",
                "9000000000000000001", "2001", "WH-1");
    }

    private String seed(QualityStatus quality, String quantity) {
        var dimension = new InventoryAccountingService.Dimension("1001", "MAT-1", "材料一", "kg", "B1", "SN-1",
                "2001", "WH-1", "3001", "LOC-1", quality, WarehouseStatus.NORMAL);
        accounting.post(new InventoryAccountingService.Posting("seed-" + quality + "-" + quantity,
                "RECEIPT", "TEST", "seed", "operator-1", List.of(
                new InventoryAccountingService.Entry(dimension, new BigDecimal(quantity)))));
        return jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE warehouse_status='NORMAL'", String.class);
    }

    private InventoryFreezeApplication.FreezeCommand freezeCommand(String key, String balanceId, String qty,
                                                                     InventoryFreezeApplication.FreezeReason reason) {
        return new InventoryFreezeApplication.FreezeCommand(key, balanceId, new BigDecimal(qty), reason,
                reason == InventoryFreezeApplication.FreezeReason.OTHER ? "现场核实" : null,
                "MANUAL", key, "operator-1");
    }

    private InventoryFreezeApplication.UnfreezeCommand unfreezeCommand(String key, String freezeId, String qty) {
        return new InventoryFreezeApplication.UnfreezeCommand(key, freezeId, new BigDecimal(qty),
                "已核实可用", "MANUAL", key, "operator-1");
    }

    @Test void partialFreezeAndUnfreezeKeepTotalAndQualityAndRetainHistory() {
        String balance = seed(QualityStatus.QUALIFIED, "1000");
        var first = freezes.freezeInventory(freezeCommand("freeze-1", balance, "200",
                InventoryFreezeApplication.FreezeReason.PACKAGE_DAMAGE));
        var second = freezes.freezeInventory(freezeCommand("freeze-2", balance, "100",
                InventoryFreezeApplication.FreezeReason.SAFETY_HOLD));
        assertThat(freezes.freezeInventory(freezeCommand("freeze-1", balance, "200",
                InventoryFreezeApplication.FreezeReason.PACKAGE_DAMAGE)).id()).isEqualTo(first.id());
        assertThatThrownBy(() -> freezes.freezeInventory(freezeCommand("freeze-1", balance, "201",
                InventoryFreezeApplication.FreezeReason.PACKAGE_DAMAGE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> freezes.freezeInventory(freezeCommand("freeze-1", balance, "200",
                InventoryFreezeApplication.FreezeReason.SAFETY_HOLD))).isInstanceOf(IllegalArgumentException.class);
        assertThat(freezes.queryActiveFreezes(balance)).hasSize(2);

        freezes.unfreezeInventory(unfreezeCommand("release-1", first.id(), "50"));
        assertThat(freezes.unfreezeInventory(unfreezeCommand("release-1", first.id(), "50")).transactionId())
                .isEqualTo(jdbc.queryForObject("SELECT transaction_id FROM wms_inventory_unfreeze WHERE request_key='release-1'", String.class));
        assertThatThrownBy(() -> freezes.unfreezeInventory(unfreezeCommand("release-1", first.id(), "40")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> freezes.unfreezeInventory(unfreezeCommand("release-too-much", first.id(), "151")))
                .isInstanceOf(IllegalStateException.class);

        var rows = queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", null,
                null, "B1", "SN-1", null, null), 1, 10)).records();
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().filter(row -> row.getWarehouseStatus() == WarehouseStatus.NORMAL)
                .map(InventoryQueryService.DetailRow::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("750");
        assertThat(rows.stream().filter(row -> row.getWarehouseStatus() == WarehouseStatus.FROZEN)
                .map(InventoryQueryService.DetailRow::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("250");
        assertThat(rows).allMatch(row -> row.getQualityStatus() == QualityStatus.QUALIFIED);
        assertThat(rows.stream().map(InventoryQueryService.DetailRow::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE transaction_type IN ('FREEZE','UNFREEZE')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry WHERE transaction_id IN " +
                "(SELECT id FROM wms_inventory_transaction WHERE transaction_type IN ('FREEZE','UNFREEZE'))", Integer.class)).isEqualTo(6);
    }

    @Test void allFreezeAndAllUnfreezeAreSupportedAndWarehouseLifecycleIsEnforced() {
        String balance = seed(QualityStatus.REJECTED, "4.5");
        var frozen = freezes.freezeInventory(freezeCommand("all-freeze", balance, "4.5",
                InventoryFreezeApplication.FreezeReason.STOCK_DISCREPANCY));
        assertThat(freezes.queryActiveFreezes(balance)).singleElement()
                .extracting(InventoryFreezeApplication.FreezeView::remainingQuantity).isEqualTo(new BigDecimal("4.500000"));
        assertThatThrownBy(() -> freezes.unfreezeInventory(new InventoryFreezeApplication.UnfreezeCommand(
                "missing-resolution", frozen.id(), BigDecimal.ONE, null, "MANUAL", "missing-resolution", "operator-1")))
                .isInstanceOf(IllegalArgumentException.class);
        freezes.unfreezeInventory(unfreezeCommand("all-release", frozen.id(), "4.5"));
        assertThat(freezes.queryActiveFreezes(balance)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT quality_status FROM wms_inventory_balance WHERE warehouse_status='NORMAL'", String.class))
                .isEqualTo("REJECTED");
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='PENDING_ACTIVATION' WHERE warehouse_id='2001'");
        assertThat(freezes.freezeInventory(freezeCommand("all-freeze", balance, "4.5",
                InventoryFreezeApplication.FreezeReason.STOCK_DISCREPANCY)).id()).isEqualTo(frozen.id());
        assertThat(freezes.unfreezeInventory(unfreezeCommand("all-release", frozen.id(), "4.5")).transactionId())
                .isEqualTo(jdbc.queryForObject("SELECT transaction_id FROM wms_inventory_unfreeze WHERE request_key='all-release'", String.class));
        assertThatThrownBy(() -> freezes.freezeInventory(freezeCommand("closed-freeze", balance, "1",
                InventoryFreezeApplication.FreezeReason.WAREHOUSE_CHECK))).isInstanceOf(IllegalStateException.class);
    }

    /** MP 维度查询必须匹配 NULL 批次和 NULL SN，不得忽略条件读出其他维度的冻结记录。 */
    @Test void typedFreezeQueriesRespectNullableBatchAndSnDimensions() {
        var withoutBatch = new InventoryAccountingService.Dimension(
                "1001", "MAT-1", "材料一", "kg", null, null, "2001", "WH-1",
                "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        var withBatch = new InventoryAccountingService.Dimension(
                "1001", "MAT-1", "材料一", "kg", "B-2", null, "2001", "WH-1",
                "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(new InventoryAccountingService.Posting("seed-null-batch", "RECEIPT", "TEST",
                "seed-null", "operator-1", List.of(new InventoryAccountingService.Entry(withoutBatch, BigDecimal.TEN))));
        accounting.post(new InventoryAccountingService.Posting("seed-present-batch", "RECEIPT", "TEST",
                "seed-present", "operator-1", List.of(new InventoryAccountingService.Entry(withBatch, BigDecimal.TEN))));
        String noBatchId = jdbc.queryForObject(
                "SELECT id FROM wms_inventory_balance WHERE batch_code IS NULL AND warehouse_status='NORMAL'",
                String.class);
        String batchId = jdbc.queryForObject(
                "SELECT id FROM wms_inventory_balance WHERE batch_code='B-2' AND warehouse_status='NORMAL'",
                String.class);
        var first = freezes.freezeInventory(freezeCommand("freeze-null-batch", noBatchId, "2",
                InventoryFreezeApplication.FreezeReason.WAREHOUSE_CHECK));
        var second = freezes.freezeInventory(freezeCommand("freeze-present-batch", batchId, "3",
                InventoryFreezeApplication.FreezeReason.SAFETY_HOLD));
        assertThat(freezes.queryActiveFreezes(noBatchId))
                .extracting(InventoryFreezeApplication.FreezeView::id).containsExactly(first.id());
        assertThat(freezes.queryActiveFreezes(batchId))
                .extracting(InventoryFreezeApplication.FreezeView::id).containsExactly(second.id());
    }

    @Test void invalidReasonAmountAndOtherWithoutNoteAreRejectedBeforePosting() {
        String balance = seed(QualityStatus.PENDING, "10");
        assertThatThrownBy(() -> freezes.freezeInventory(freezeCommand("bad-amount", balance, "0",
                InventoryFreezeApplication.FreezeReason.OTHER))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> freezes.freezeInventory(new InventoryFreezeApplication.FreezeCommand("bad-other",
                balance, BigDecimal.ONE, InventoryFreezeApplication.FreezeReason.OTHER, null,
                "MANUAL", "bad-other", "operator-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> freezes.freezeInventory(new InventoryFreezeApplication.FreezeCommand("bad-code",
                balance, BigDecimal.ONE, null, null, "MANUAL", "bad-code", "operator-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> freezes.freezeInventory(freezeCommand("insufficient", balance, "11",
                InventoryFreezeApplication.FreezeReason.WAREHOUSE_CHECK))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_freeze", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE transaction_type='FREEZE'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("10");
    }

    @Test void concurrentPartialUnfreezeNeverExceedsRemainingAndUsesDatabaseLock() throws Exception {
        String balance = seed(QualityStatus.QUALIFIED, "100");
        var frozen = freezes.freezeInventory(freezeCommand("concurrent-freeze", balance, "100",
                InventoryFreezeApplication.FreezeReason.WAREHOUSE_CHECK));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("release-a", "release-b").stream().map(key -> pool.submit(() -> {
                ready.countDown(); start.await();
                try { freezes.unfreezeInventory(unfreezeCommand(key, frozen.id(), "70")); return true; }
                catch (IllegalStateException expectedConflict) { return false; }
            })).toList();
            ready.await(); start.countDown();
            assertThat(tasks.stream().filter(task -> { try { return task.get(); } catch (Exception e) { throw new RuntimeException(e); } }).count()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM wms_inventory_freeze WHERE id=?", BigDecimal.class, frozen.id()))
                .isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("100");
    }
}
