package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.Filter;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication.FreezeCommand;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication.FreezeReason;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication.UnfreezeCommand;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** PostgreSQL 真实 SQL、不可变快照、分录分页与资源服务授权契约。 */
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.test.context.SpringBootTest(classes = MomWmsApplication.class, webEnvironment = MOCK,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=true",
                "management.health.redis.enabled=false"})
class InventoryHistoryPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_wms");
        registry.add("spring.flyway.default-schema", () -> "mom_wms");
    }
    @Autowired InventoryAccountingService accounting;
    @Autowired InventoryHistoryApplication history;
    @Autowired InventoryFreezeApplication freezes;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES(?,?,?,'INITIALIZING') ON CONFLICT(warehouse_id) DO UPDATE SET state='INITIALIZING'",
                "9000000000000000001", "2001", "WH-1");
        jdbc.execute("TRUNCATE wms_inventory_unfreeze, wms_inventory_freeze, wms_inventory_transaction_entry, wms_inventory_transaction, wms_inventory_balance");
    }

    private Dimension dimension(String material, String batch, String sn, String location) {
        return new Dimension(material, "MAT-" + material, "历史物料", "kg", batch, sn,
                "2001", "WH-1", location, "LOC-" + location, QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
    }
    private Filter filter(String material, String batch, String sn, String warehouse, String location,
                          String type, String source, String sourceId, String actor) {
        return new Filter(material, batch, sn, warehouse, location, type, source, sourceId, actor,
                null, null, null,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test void multiEntryHistoryPreservesSignsDimensionSnapshotsFiltersAndFullDetail() {
        Dimension a = dimension("1001", "B1", "SN-A", "3001");
        Dimension b = dimension("1002", "B2", "SN-B", "3002");
        String opening = accounting.post(new Posting("history-opening", "OPENING", "OPENING_DOCUMENT", "document-1",
                "operator-1", List.of(new Entry(a, new BigDecimal("5")), new Entry(b, new BigDecimal("2")))));
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='ACTIVE' WHERE warehouse_id='2001'");
        String move = accounting.post(new Posting("history-move", "LOCATION_TRANSFER", "MANUAL", "move-1",
                "operator-2", List.of(new Entry(a, new BigDecimal("-5")),
                        new Entry(dimension("1001", "B1", "SN-A", "3003"), new BigDecimal("5")))));
        var detail = history.transaction(move);
        assertThat(detail.getEntries()).extracting(e -> e.getDeltaQuantity().toPlainString()).containsExactly("-5.000000", "5.000000");
        assertThat(detail.getEntries()).extracting(e -> e.getLineNo()).containsExactly(1, 2);
        assertThat(detail.getEntries()).extracting(e -> e.getLocationId()).containsExactly("3001", "3003");
        assertThat(detail.getEntries()).extracting(e -> e.getBeforeQuantity().toPlainString()).containsExactly("5.000000", "0.000000");
        assertThat(detail.getEntries()).extracting(e -> e.getAfterQuantity().toPlainString()).containsExactly("0.000000", "5.000000");
        assertThat(history.search(new PageQuery<>(filter("1001", "B1", "SN-A", "2001", "3001",
                "LOCATION_TRANSFER", "MANUAL", "move-1", "operator-2"), 1, 10)).records())
                .extracting(e -> e.getDeltaQuantity().signum()).containsExactly(-1);
        assertThat(history.search(new PageQuery<>(filter("1002", "B2", "SN-B", "2001", "3002",
                "OPENING", "OPENING_DOCUMENT", "document-1", "operator-1"), 1, 10)).records()).hasSize(1);
        assertThat(history.transaction(opening).getEntries()).hasSize(2);
        assertThat(history.search(new PageQuery<>(filter("1001", null, null, null, null,
                null, null, null, null), 1, 1)).total()).isEqualTo(3);
        jdbc.update("UPDATE wms_inventory_balance SET material_name='当前名称', location_code='CURRENT' WHERE material_id='1001'");
        assertThat(history.transaction(move).getEntries().getFirst().getMaterialName()).isEqualTo("历史物料");
        assertThat(history.transaction(move).getEntries().getFirst().getLocationCode()).isEqualTo("LOC-3001");
    }

    @Test void oldFactsRemainNullAndZeroBalancesRemainQueryable() {
        jdbc.update("INSERT INTO wms_inventory_transaction(id,request_key,request_hash) VALUES ('9000000000000000011','old-history',repeat('a',64))");
        jdbc.update("INSERT INTO wms_inventory_transaction_entry(id,transaction_id,line_no,balance_id,delta_quantity) VALUES ('9000000000000000012','9000000000000000011',1,'9000000000000000013',-1)");
        var page = history.search(new PageQuery<>(filter(null,null,null,null,null,null,null,null,null), 1, 10));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().getFirst().getMaterialId()).isNull();
        assertThat(page.records().getFirst().getBeforeQuantity()).isNull();
        assertThat(history.transaction("9000000000000000011").getEntries()).hasSize(1);
    }

    @Test void searchesHistoricalMaterialNameAndCodesWithoutTechnicalIds() {
        accounting.post(new Posting("history-readable-search", "OPENING", "OPENING_DOCUMENT", "document-search",
                "operator-1", List.of(new Entry(dimension("1001", "B1", null, "3001"), new BigDecimal("5")),
                        new Entry(dimension("1002", "B2", null, "3002"), new BigDecimal("2")))));
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);
        Filter byName = new Filter(null, null, null, null, null, null, null, null, null,
                "历史物料", "WH-1", "LOC-3001", start, end);
        assertThat(history.search(new PageQuery<>(byName, 1, 10)).records())
                .extracting(e -> e.getMaterialCode()).containsExactly("MAT-1001");
        Filter byCode = new Filter(null, null, null, null, null, null, null, null, null,
                "1002", "WH-1", "LOC-3002", start, end);
        assertThat(history.search(new PageQuery<>(byCode, 1, 10)).records())
                .extracting(e -> e.getMaterialCode()).containsExactly("MAT-1002");
    }

    @Test void freezeAndClosedRestrictionRemainInHistoryWithOriginalReason() {
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='ACTIVE' WHERE warehouse_id='2001'");
        accounting.post(new Posting("history-seed", "RECEIPT", "TEST", "receipt-1", "operator-1",
                List.of(new Entry(dimension("1001", "B1", null, "3001"), new BigDecimal("5")))));
        String balanceId = jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE warehouse_status='NORMAL'", String.class);
        var freeze = freezes.freezeInventory(new FreezeCommand("history-freeze", balanceId, new BigDecimal("2"),
                FreezeReason.PACKAGE_DAMAGE, "外箱破损", "MANUAL", "freeze-1", "operator-2"));
        var release = freezes.unfreezeInventory(new UnfreezeCommand("history-unfreeze", freeze.id(), new BigDecimal("2"),
                "现场复核完成", "MANUAL", "unfreeze-1", "operator-3"));
        assertThat(freeze.remainingQuantity()).isEqualByComparingTo("2");
        assertThat(release.remainingQuantity()).isEqualByComparingTo("0");
        var frozenHistory = history.transaction(freeze.transactionId());
        assertThat(frozenHistory.getReasonCode()).isEqualTo("PACKAGE_DAMAGE");
        assertThat(frozenHistory.getNote()).isEqualTo("外箱破损");
        assertThat(frozenHistory.getEntries()).extracting(e -> e.getDeltaQuantity().signum()).containsExactly(-1, 1);
        var releasedHistory = history.transaction(release.transactionId());
        assertThat(releasedHistory.getReasonCode()).isEqualTo("PACKAGE_DAMAGE");
        assertThat(releasedHistory.getNote()).isEqualTo("现场复核完成");
        assertThat(releasedHistory.getEntries()).extracting(e -> e.getDeltaQuantity().signum()).containsExactly(1, -1);
        assertThat(history.search(new PageQuery<>(filter("1001", "B1", null, "2001", "3001",
                "FREEZE", "MANUAL", "freeze-1", "operator-2"), 1, 10)).total()).isEqualTo(2);
    }

    @Test void timeAndPageBoundariesRejectInvalidRequests() {
        Filter tooLong = new Filter(null,null,null,null,null,null,null,null,null,null,null,null,
                Instant.now().minus(367, ChronoUnit.DAYS), Instant.now());
        assertThatThrownBy(() -> history.search(new PageQuery<>(tooLong, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.search(new PageQuery<>(filter(null,null,null,null,null,null,null,null,null), 1, 100000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void partialUnfreezesKeepTheirOwnNotesAndOriginalReasonWithoutDuplicateEntries() {
        jdbc.update("UPDATE wms_warehouse_inventory_state SET state='ACTIVE' WHERE warehouse_id='2001'");
        accounting.post(new Posting("history-multiple-seed", "RECEIPT", "TEST", "receipt-multiple", "operator-1",
                List.of(new Entry(dimension("1001", "B1", null, "3001"), new BigDecimal("10")))));
        String balanceId = jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE warehouse_status='NORMAL'", String.class);
        var damaged = freezes.freezeInventory(new FreezeCommand("history-damaged", balanceId, new BigDecimal("4"),
                FreezeReason.PACKAGE_DAMAGE, "破损", "MANUAL", "freeze-damaged", "operator-2"));
        var checking = freezes.freezeInventory(new FreezeCommand("history-checking", balanceId, new BigDecimal("3"),
                FreezeReason.WAREHOUSE_CHECK, "盘点", "MANUAL", "freeze-checking", "operator-3"));
        var first = freezes.unfreezeInventory(new UnfreezeCommand("history-release-first", damaged.id(), new BigDecimal("1"),
                "首批复核", "MANUAL", "release-first", "operator-4"));
        var second = freezes.unfreezeInventory(new UnfreezeCommand("history-release-second", damaged.id(), new BigDecimal("3"),
                "后续复核", "MANUAL", "release-second", "operator-5"));
        assertThat(history.transaction(first.transactionId()).getReasonCode()).isEqualTo("PACKAGE_DAMAGE");
        assertThat(history.transaction(first.transactionId()).getNote()).isEqualTo("首批复核");
        assertThat(history.transaction(second.transactionId()).getReasonCode()).isEqualTo("PACKAGE_DAMAGE");
        assertThat(history.transaction(second.transactionId()).getNote()).isEqualTo("后续复核");
        assertThat(history.transaction(checking.transactionId()).getReasonCode()).isEqualTo("WAREHOUSE_CHECK");
        var page = history.search(new PageQuery<>(filter("1001", "B1", null, "2001", "3001",
                null, null, null, null), 1, 20));
        assertThat(page.total()).isEqualTo(9);
        assertThat(page.records()).extracting(e -> e.getId()).doesNotHaveDuplicates();
    }

    @Test void bothHistoryEndpointsRequireReadAuthority() throws Exception {
        String id = accounting.post(new Posting("history-http", "OPENING", "OPENING_DOCUMENT", "document-http", "operator-1",
                List.of(new Entry(dimension("1001", null, null, "3001"), new BigDecimal("2")))));
        String body = "{\"params\":{\"startTime\":\"2000-01-01T00:00:00Z\",\"endTime\":\"2001-01-01T00:00:00Z\"},\"pageNo\":1,\"pageSize\":10}";
        mvc.perform(post("/inventory/history/search").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/inventory/history/search").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:opening:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/inventory/transactions/" + id).with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:opening:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/inventory/transactions/" + id).with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries[0].deltaQuantity").value("2.000000"));
        mvc.perform(get("/inventory/transactions/9000000000000000018")
                .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/inventory/history/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{},\"pageNo\":1,\"pageSize\":10}")
                .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isBadRequest());
    }
}
