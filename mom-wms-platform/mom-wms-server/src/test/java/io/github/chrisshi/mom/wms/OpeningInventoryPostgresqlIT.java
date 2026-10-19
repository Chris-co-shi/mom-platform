package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.LineInput;
import io.github.chrisshi.mom.wms.application.OpeningInventoryCommitService;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.MaterialSnapshot;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningInventoryMapper;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用 PostgreSQL 验证期初单、库存记账、幂等、回滚、并发及仓库启用状态。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=false"})
class OpeningInventoryPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired OpeningInventoryCommitService commits;
    @Autowired OpeningInventoryMapper mapper;
    @Autowired InventoryQueryService queries;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE wms_opening_document_line,wms_opening_document,wms_warehouse_inventory_state," +
                "wms_inventory_transaction_entry,wms_inventory_transaction,wms_inventory_balance CASCADE");
    }

    @Test void postsMultipleLinesOnceAndActivationIsSeparate() {
        String documentId = createDraft("2000000000000000001");
        add(documentId, "SN-A", "3001", "4.25");
        add(documentId, "SN-B", "3002", "2.75");
        var document = mapper.find(documentId);
        assertThat(document.warehouseState()).isEqualTo("INITIALIZING");
        String transactionId = commits.post(documentId, document.version(), "operator-1");
        assertThat(commits.post(documentId, document.version(), "operator-1")).isEqualTo(transactionId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction WHERE id=? AND transaction_type='OPENING' AND source_type='OPENING_DOCUMENT' AND source_id=? AND operator_id='operator-1'", Integer.class, transactionId, documentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry WHERE transaction_id=?", Integer.class, transactionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM wms_opening_document WHERE id=?", String.class, documentId)).isEqualTo("POSTED");
        assertThat(mapper.find(documentId).warehouseState()).isEqualTo("PENDING_ACTIVATION");
        assertThat(queries.details(new io.github.chrisshi.mom.core.page.PageQuery<>(
                new InventoryQueryService.DetailFilter("1001", "2000000000000000001", null,
                        null, null, null, null), 1, 10)).records()).hasSize(2);
        commits.activate("2000000000000000001", "operator-2");
        assertThat(mapper.find(documentId).warehouseState()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> commits.activate("2000000000000000001", "operator-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> createDraft("2000000000000000001")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> commits.deleteDraft(documentId)).isInstanceOf(IllegalStateException.class);
    }

    @Test void duplicateSnAcrossLinesRollsBackWholeOpeningTransaction() {
        String documentId = createDraft("2000000000000000002");
        add(documentId, "SN-DUP", "3001", "1");
        add(documentId, "SN-DUP", "3002", "1");
        var document = mapper.find(documentId);
        assertThatThrownBy(() -> commits.post(documentId, document.version(), "operator-1"))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction_entry", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_balance", Integer.class)).isZero();
        assertThat(mapper.find(documentId).status()).isEqualTo("DRAFT");
        assertThat(mapper.find(documentId).warehouseState()).isEqualTo("INITIALIZING");
    }

    @Test void concurrentPostForSameDocumentCreatesOneTransaction() throws Exception {
        String documentId = createDraft("2000000000000000003");
        add(documentId, null, "3001", "3");
        long version = mapper.find(documentId).version();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return commits.post(documentId, version, "operator-1"); });
            var two = pool.submit(() -> { start.await(); return commits.post(documentId, version, "operator-1"); });
            start.countDown();
            assertThat(one.get()).isEqualTo(two.get());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wms_inventory_transaction", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM wms_inventory_balance", BigDecimal.class)).isEqualByComparingTo("3");
    }

    private String createDraft(String warehouseId) {
        String id = ids.nextId();
        commits.create(id, "OB-" + id, new WarehouseResourceReference("WAREHOUSE", warehouseId, "WH-1", "主仓",
                "1000000000000000001", "1000000000000000001", "Asia/Shanghai", null, 0L, true, null), "operator-1");
        return id;
    }

    private void add(String documentId, String sn, String locationId, String quantity) {
        var document = mapper.find(documentId);
        commits.addLine(document, new MaterialSnapshot("1001", "MAT-1", "物料一", "REQUIRED", "kg"),
                new LocationSnapshot(locationId, "LOC-" + locationId),
                new LineInput("1001", locationId, "BATCH-1", sn, QualityStatus.QUALIFIED,
                        WarehouseStatus.NORMAL, new BigDecimal(quantity)), ids.nextId());
    }
}
