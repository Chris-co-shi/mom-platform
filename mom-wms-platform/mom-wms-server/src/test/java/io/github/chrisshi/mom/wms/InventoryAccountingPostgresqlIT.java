package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用真实 PostgreSQL 检查库存交易的原子性、幂等与并发扣减。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=false"})
class InventoryAccountingPostgresqlIT {
    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform").withUsername("mom").withPassword("mom");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired InventoryAccountingService accounting;
    @Autowired InventoryQueryService queries;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE wms_inventory_transaction_entry, wms_inventory_transaction, wms_inventory_balance, wms_warehouse_inventory_state");
        jdbc.update("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES(?,?,?,?)",
                "9000000000000000001", "2001", "WH-1", "ACTIVE");
    }

    private Dimension dimension(String sn, QualityStatus quality, WarehouseStatus warehouse) {
        return new Dimension("1001", "MAT-1", "材料一", "kg", "B1", sn,
                "2001", "WH-1", "3001", "LOC-1", quality, warehouse);
    }

    private Posting posting(String key, Entry... entries) {
        return new Posting(key, "RECEIPT", "TEST", "source-" + key, "operator-1", List.of(entries));
    }

    private Entry entry(Dimension dimension, String quantity) {
        return new Entry(dimension, new BigDecimal(quantity));
    }

    @Test
    void normalInventoryTransactionRequiresActiveWarehouse() {
        Dimension unopened = new Dimension("1001", "MAT-1", "材料一", "kg", null, null,
                "2999", "WH-2", "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        assertThatThrownBy(() -> accounting.post(posting("unopened-warehouse", entry(unopened, "1"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isZero();
    }

    @Test
    void writesBalanceTransactionAndEntriesOnceAndRejectsRepeatedSnTags() {
        var qualified = dimension("SN-1", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        var frozen = dimension("SN-2", QualityStatus.QUALIFIED, WarehouseStatus.FROZEN);
        var request = posting("initial-1", entry(qualified, "10.500000"), entry(frozen, "2"));
        String id = accounting.post(request);
        assertThat(accounting.post(request)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("12.500000");
        assertThat(jdbc.queryForMap("SELECT transaction_type, source_type, source_id, operator_id " +
                "FROM wms_inventory_transaction WHERE id = ?", id))
                .containsEntry("transaction_type", "RECEIPT")
                .containsEntry("source_type", "TEST")
                .containsEntry("source_id", "source-initial-1")
                .containsEntry("operator_id", "operator-1");
        var firstEntry = jdbc.queryForMap("""
                SELECT material_id, batch_code, sn_code, warehouse_id, location_id, quality_status,
                       warehouse_status, delta_quantity, before_quantity, after_quantity
                  FROM wms_inventory_transaction_entry WHERE transaction_id = ? AND line_no = 1
                """, id);
        assertThat(firstEntry).containsEntry("material_id", "1001")
                .containsEntry("batch_code", "B1").containsEntry("sn_code", "SN-1")
                .containsEntry("warehouse_id", "2001").containsEntry("location_id", "3001")
                .containsEntry("quality_status", "QUALIFIED").containsEntry("warehouse_status", "NORMAL")
                .containsEntry("delta_quantity", new BigDecimal("10.500000"))
                .containsEntry("before_quantity", new BigDecimal("0.000000"))
                .containsEntry("after_quantity", new BigDecimal("10.500000"));
        assertThatThrownBy(() -> accounting.post(posting("initial-1", entry(qualified, "1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounting.post(new Posting("initial-1", "RECEIPT", "TEST",
                "source-initial-1", "operator-2", request.entries())))
                .isInstanceOf(IllegalArgumentException.class);
        var overview = queries.overview(new PageQuery<>(new InventoryQueryService.OverviewFilter("1001", null, null, null), 1, 10));
        assertThat(overview.records()).hasSize(1);
        assertThat(overview.records().getFirst().getTotalQuantity()).isEqualByComparingTo("12.5");
        assertThat(overview.records().getFirst().getAvailableQuantity()).isEqualByComparingTo("10.5");
        assertThat(overview.records().getFirst().getUnavailableQuantity()).isEqualByComparingTo("2");
        assertThat(overview.records().getFirst().getFrozenQuantity()).isEqualByComparingTo("2");
        var details = queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", null, null,
                null, "SN-1", null, null), 1, 10));
        assertThat(details.records()).hasSize(1);
        var secondPage = queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", null, null,
                null, "SN-1", null, null), 2, 1));
        assertThat(secondPage.records()).isEmpty();
        assertThat(secondPage.total()).isEqualTo(1);
        assertThatThrownBy(() -> accounting.post(posting("duplicate-sn", entry(
                dimension("SN-1", QualityStatus.REJECTED, WarehouseStatus.NORMAL), "1"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE request_key='duplicate-sn'", Integer.class)).isZero();
    }

    @Test
    void failedSecondEntryRollsBackFirstEntryAndTransaction() {
        var first = dimension(null, QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        var second = dimension("SN-2", QualityStatus.REJECTED, WarehouseStatus.NORMAL);
        assertThatThrownBy(() -> accounting.post(posting("bad-1", entry(first, "4"), entry(second, "-1"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isZero();
    }

    @Test
    void allQualityStatesAndFrozenBalancesFollowPhaseOneAvailabilityRule() throws Exception {
        accounting.post(posting("state-matrix",
                entry(dimension(null, QualityStatus.QUALIFIED, WarehouseStatus.NORMAL), "5"),
                entry(dimension(null, QualityStatus.PENDING, WarehouseStatus.NORMAL), "2"),
                entry(dimension(null, QualityStatus.RESTRICTED, WarehouseStatus.NORMAL), "3"),
                entry(dimension(null, QualityStatus.REJECTED, WarehouseStatus.NORMAL), "4"),
                entry(dimension(null, QualityStatus.PENDING, WarehouseStatus.FROZEN), "6")));
        var row = queries.overview(new PageQuery<>(new InventoryQueryService.OverviewFilter("1001", null, null, null), 1, 10))
                .records().getFirst();
        assertThat(row.getTotalQuantity()).isEqualByComparingTo("20");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("5");
        assertThat(row.getUnavailableQuantity()).isEqualByComparingTo("15");
        assertThat(row.getFrozenQuantity()).isEqualByComparingTo("6");
        var filtered = queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", "2001", null,
                null, null, QualityStatus.PENDING, WarehouseStatus.FROZEN), 1, 10)).records();
        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst().getQualityStatus()).isEqualTo(QualityStatus.PENDING);
        assertThat(filtered.getFirst().getWarehouseStatus()).isEqualTo(WarehouseStatus.FROZEN);
        var objectMapper = new ObjectMapper();
        assertThat(objectMapper.writeValueAsString(QualityStatus.QUALIFIED)).isEqualTo("\"QUALIFIED\"");
        assertThat(objectMapper.writeValueAsString(WarehouseStatus.FROZEN)).isEqualTo("\"FROZEN\"");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                 WHERE c.relname='uk_wms_inventory_balance_dimension' AND i.indnullsnotdistinct
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void historicalRowsRejectUpdateAndDelete() {
        var stock = dimension(null, QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(posting("history-1", entry(stock, "2")));
        assertThatThrownBy(() -> jdbc.update("UPDATE wms_inventory_transaction SET request_hash='changed'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM wms_inventory_transaction_entry"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void genericBalanceMapperWritesAreBlockedOutsideAccountingEntry() {
        var operationNames = java.util.Arrays.stream(InventoryAccountingMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();
        assertThat(InventoryAccountingMapper.class.getInterfaces()).isEmpty();
        // 权威余额只允许列出的原子数据库动作，不暴露 BaseMapper 通用插入/删除/更新。
        assertThat(operationNames).containsExactlyInAnyOrder(
                "claim", "hashOf", "idOf", "increase", "decrease", "insertEntry",
                "warehouseInventoryState", "findInventoryBalance", "lockAccountingKey",
                "positiveBalanceCountForSn", "findTransferReplay", "countLockOwner", "confirmedCountOwner");
        assertThat(io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryFreezeMapper.class.getInterfaces())
                .isEmpty();
        assertThat(java.util.Arrays.stream(
                io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryFreezeMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList())
                .containsExactlyInAnyOrder(
                        "lockFreeze", "insertFreeze", "decreaseFreezeRemaining", "insertUnfreeze");
        // 两个单表读 Mapper 复用平台能力；只有锁定和原子写 SQL 不开放普通写接口。
        assertThat(io.github.chrisshi.mom.data.mapper.MomBaseMapper.class.isAssignableFrom(
                io.github.chrisshi.mom.wms.infrastructure.query.InventoryFreezeQueryMapper.class)).isTrue();
        assertThat(io.github.chrisshi.mom.data.mapper.MomBaseMapper.class.isAssignableFrom(
                io.github.chrisshi.mom.wms.infrastructure.query.InventoryUnfreezeQueryMapper.class)).isTrue();
    }

    @Test
    void repeatedDimensionEntriesCaptureSequentialBeforeAndAfterQuantities() {
        var stock = dimension("SN-SEQ", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(posting("sequence-1", entry(stock, "10"), entry(stock, "-3"), entry(stock, "2")));
        var quantities = jdbc.query("""
                SELECT before_quantity, after_quantity FROM wms_inventory_transaction_entry
                 WHERE transaction_id = (SELECT id FROM wms_inventory_transaction WHERE request_key = 'sequence-1')
                 ORDER BY line_no
                """, (rs, rowNum) -> List.of(rs.getBigDecimal("before_quantity"),
                rs.getBigDecimal("after_quantity")));
        assertThat(quantities).containsExactly(
                List.of(new BigDecimal("0.000000"), new BigDecimal("10.000000")),
                List.of(new BigDecimal("10.000000"), new BigDecimal("7.000000")),
                List.of(new BigDecimal("7.000000"), new BigDecimal("9.000000")));
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("9");
    }

    @Test
    void legacyPhaseOneRowsRemainRepresentableWithoutInventedFacts() {
        jdbc.update("""
                INSERT INTO wms_inventory_transaction (id, request_key, request_hash, occurred_at)
                VALUES ('9001', 'legacy-1', repeat('a', 64), now())
                """);
        jdbc.update("""
                INSERT INTO wms_inventory_transaction_entry (id, transaction_id, line_no, balance_id, delta_quantity)
                VALUES ('9002', '9001', 1, '9003', 4)
                """);
        assertThat(jdbc.queryForMap("SELECT transaction_type, source_type, source_id, operator_id " +
                "FROM wms_inventory_transaction WHERE id = '9001'"))
                .containsEntry("transaction_type", null).containsEntry("source_type", null)
                .containsEntry("source_id", null).containsEntry("operator_id", null);
        assertThat(jdbc.queryForMap("SELECT material_id, quality_status, before_quantity, after_quantity " +
                "FROM wms_inventory_transaction_entry WHERE id = '9002'"))
                .containsEntry("material_id", null).containsEntry("quality_status", null)
                .containsEntry("before_quantity", null).containsEntry("after_quantity", null);
    }

    @Test
    void sameLocationCannotBeRebookedUnderAnotherWarehouse() {
        var stock = dimension(null, QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(posting("warehouse-origin", entry(stock, "2")));
        var wrongWarehouse = new Dimension(stock.materialId(), stock.materialCode(), stock.materialName(),
                stock.uomCode(), stock.batchCode(), stock.snCode(), "2999", "WH-2", stock.locationId(),
                stock.locationCode(), stock.qualityStatus(), stock.warehouseStatus());
        assertThatThrownBy(() -> accounting.post(posting("warehouse-conflict", entry(wrongWarehouse, "3"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("2");
    }

    @Test
    void concurrentDeductionsCannotOverdraw() throws Exception {
        var stock = dimension("SN-3", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(posting("seed-1", entry(stock, "1")));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> deduct("take-1", stock, ready, start));
            Future<Boolean> second = pool.submit(() -> deduct("take-2", stock, ready, start));
            ready.await(); start.countDown();
            assertThat((first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("0");
        assertThat(queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", null, null,
                null, null, null, null), 1, 10)).records()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentSameBusinessKeyPostsOnlyOnce() throws Exception {
        var request = posting("parallel-same-key", entry(dimension(null, QualityStatus.QUALIFIED,
                WarehouseStatus.NORMAL), "7"));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(() -> postTogether(request, ready, start));
            Future<String> second = pool.submit(() -> postTogether(request, ready, start));
            ready.await(); start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("7");
    }

    private String postTogether(Posting request, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); start.await(); return accounting.post(request);
    }

    private boolean deduct(String key, Dimension stock, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); start.await();
        try { accounting.post(posting(key, entry(stock, "-1"))); return true; }
        catch (IllegalStateException expected) { return false; }
    }
}
