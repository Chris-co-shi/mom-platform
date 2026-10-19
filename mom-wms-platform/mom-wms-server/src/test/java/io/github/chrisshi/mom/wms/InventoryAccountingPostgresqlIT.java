package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
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
        jdbc.execute("TRUNCATE wms_inventory_transaction_entry, wms_inventory_transaction, wms_inventory_balance");
    }

    private Dimension dimension(String sn, String quality, String warehouse) {
        return new Dimension("1001", "MAT-1", "材料一", "kg", "B1", sn,
                "2001", "WH-1", "3001", "LOC-1", quality, warehouse);
    }

    private Posting posting(String key, Entry... entries) {
        return new Posting(key, List.of(entries));
    }

    private Entry entry(Dimension dimension, String quantity) {
        return new Entry(dimension, new BigDecimal(quantity));
    }

    @Test
    void writesBalanceTransactionAndEntriesOnceAndAllowsOneSnInDifferentStatuses() {
        var qualified = dimension("SN-1", "QUALIFIED", "NORMAL");
        var frozen = dimension("SN-1", "QUALIFIED", "FROZEN");
        var request = posting("initial-1", entry(qualified, "10.500000"), entry(frozen, "2"));
        String id = accounting.post(request);
        assertThat(accounting.post(request)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM wms_inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("12.500000");
        assertThatThrownBy(() -> accounting.post(posting("initial-1", entry(qualified, "1"))))
                .isInstanceOf(IllegalArgumentException.class);
        var overview = queries.overview(new PageQuery<>(new InventoryQueryService.OverviewFilter("1001", null, null), 1, 10));
        assertThat(overview.records()).hasSize(1);
        assertThat(overview.records().getFirst().getTotalQuantity()).isEqualByComparingTo("12.5");
        assertThat(overview.records().getFirst().getAvailableQuantity()).isEqualByComparingTo("10.5");
        assertThat(overview.records().getFirst().getUnavailableQuantity()).isEqualByComparingTo("2");
        assertThat(overview.records().getFirst().getFrozenQuantity()).isEqualByComparingTo("2");
        var details = queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", null, null,
                null, "SN-1", null, null), 1, 10));
        assertThat(details.records()).hasSize(2);
        var secondPage = queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", null, null,
                null, "SN-1", null, null), 2, 1));
        assertThat(secondPage.records()).hasSize(1);
        assertThat(secondPage.total()).isEqualTo(2);
    }

    @Test
    void failedSecondEntryRollsBackFirstEntryAndTransaction() {
        var first = dimension(null, "QUALIFIED", "NORMAL");
        var second = dimension("SN-2", "REJECTED", "NORMAL");
        assertThatThrownBy(() -> accounting.post(posting("bad-1", entry(first, "4"), entry(second, "-1"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isZero();
    }

    @Test
    void allQualityStatesAndFrozenBalancesFollowPhaseOneAvailabilityRule() {
        accounting.post(posting("state-matrix",
                entry(dimension(null, "QUALIFIED", "NORMAL"), "5"),
                entry(dimension(null, "PENDING", "NORMAL"), "2"),
                entry(dimension(null, "RESTRICTED", "NORMAL"), "3"),
                entry(dimension(null, "REJECTED", "NORMAL"), "4"),
                entry(dimension(null, "PENDING", "FROZEN"), "6")));
        var row = queries.overview(new PageQuery<>(new InventoryQueryService.OverviewFilter("1001", null, null), 1, 10))
                .records().getFirst();
        assertThat(row.getTotalQuantity()).isEqualByComparingTo("20");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("5");
        assertThat(row.getUnavailableQuantity()).isEqualByComparingTo("15");
        assertThat(row.getFrozenQuantity()).isEqualByComparingTo("6");
        assertThat(queries.details(new PageQuery<>(new InventoryQueryService.DetailFilter("1001", "2001", null,
                null, null, "PENDING", "FROZEN"), 1, 10)).records()).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                 WHERE c.relname='uk_wms_inventory_balance_dimension' AND i.indnullsnotdistinct
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void historicalRowsRejectUpdateAndDelete() {
        var stock = dimension(null, "QUALIFIED", "NORMAL");
        accounting.post(posting("history-1", entry(stock, "2")));
        assertThatThrownBy(() -> jdbc.update("UPDATE wms_inventory_transaction SET request_hash='changed'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM wms_inventory_transaction_entry"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void sameLocationCannotBeRebookedUnderAnotherWarehouse() {
        var stock = dimension(null, "QUALIFIED", "NORMAL");
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
        var stock = dimension("SN-3", "QUALIFIED", "NORMAL");
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
        var request = posting("parallel-same-key", entry(dimension(null, "QUALIFIED", "NORMAL"), "7"));
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
