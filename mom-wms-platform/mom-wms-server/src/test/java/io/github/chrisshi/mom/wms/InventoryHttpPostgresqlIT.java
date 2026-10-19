package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
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
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** 真实 Web/Security/SQL/i18n 端到端进程内契约，不声称覆盖 Redis Token 内省。 */
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.test.context.SpringBootTest(classes = MomWmsApplication.class, webEnvironment = MOCK,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=true",
                "management.health.redis.enabled=false"})
class InventoryHttpPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_wms");
        registry.add("spring.flyway.default-schema", () -> "mom_wms");
    }
    @Autowired WebApplicationContext context;
    @Autowired InventoryAccountingService accounting;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("INSERT INTO wms_warehouse_inventory_state(id,warehouse_id,warehouse_code,state) VALUES(?,?,?,'ACTIVE') ON CONFLICT(warehouse_id) DO NOTHING",
                "9000000000000000001", "2001", "WH-1");
        jdbc.execute("TRUNCATE wms_inventory_unfreeze, wms_inventory_freeze, wms_inventory_transaction_entry, wms_inventory_transaction, wms_inventory_balance");
        var dimension = new Dimension("1001", "MAT-1", "材料一", "kg", null, "SN-1",
                "2001", "WH-1", "3001", "LOC-1", QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        accounting.post(new Posting("http-seed", "RECEIPT", "TEST", "http-seed", "operator-1",
                List.of(new Entry(dimension, new BigDecimal("12.500000")))));
    }

    @Test
    void authenticatedReadReturnsDecimalStringsAndEnforcesWmsAuthority() throws Exception {
        String request = """
                {"params":{"materialId":"1001"},"pageNo":1,"pageSize":10}
                """;
        mvc.perform(post("/inventory/overview/search").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/inventory/overview/search").contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("mdm:material:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/inventory/overview/search").contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].materialCode").value("MAT-1"))
                .andExpect(jsonPath("$.data.records[0].totalQuantity").value("12.500000"));
        mvc.perform(post("/inventory/details/search").contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].snCode").value("SN-1"))
                .andExpect(jsonPath("$.data.records[0].qualityStatus").value("QUALIFIED"))
                .andExpect(jsonPath("$.data.records[0].warehouseStatus").value("NORMAL"))
                .andExpect(jsonPath("$.data.records[0].quantity").value("12.500000"));
    }

    @Test
    void wmsOwnerPublishesInventoryText() throws Exception {
        mvc.perform(get("/i18n/runtime/bundles").param("locale", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages['wms.inventory.overview']").value("实时库存总览"))
                .andExpect(jsonPath("$.data.messages['wms.inventory.reason.package_damage']").value("包装破损"));
    }

    @Test
    void openingDocumentReadUsesSeparateWmsPermission() throws Exception {
        String request = "{\"params\":{},\"pageNo\":1,\"pageSize\":10}";
        mvc.perform(post("/opening-documents/search").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isUnauthorized());
        mvc.perform(post("/opening-documents/search").contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/opening-documents/search").contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:opening:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void freezeAndUnfreezeRequireTheirWmsWriteAuthorities() throws Exception {
        String balanceId = jdbc.queryForObject("SELECT id FROM wms_inventory_balance WHERE warehouse_status='NORMAL'", String.class);
        String freeze = "{\"requestKey\":\"http-freeze\",\"balanceId\":\"" + balanceId +
                "\",\"quantity\":\"2\",\"reason\":\"PACKAGE_DAMAGE\"}";
        mvc.perform(post("/inventory/freezes").contentType(MediaType.APPLICATION_JSON).content(freeze)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isForbidden());
        var response = mvc.perform(post("/inventory/freezes").contentType(MediaType.APPLICATION_JSON).content(freeze)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:freeze"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reasonCode").value("PACKAGE_DAMAGE"))
                .andReturn();
        String freezeId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response.getResponse().getContentAsString()).at("/data/id").asText();
        String unfreeze = "{\"requestKey\":\"http-release\",\"quantity\":\"1\",\"note\":\"核实完成\"}";
        mvc.perform(post("/inventory/freezes/" + freezeId + "/unfreeze").contentType(MediaType.APPLICATION_JSON).content(unfreeze)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:freeze"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/inventory/freezes/" + freezeId + "/unfreeze").contentType(MediaType.APPLICATION_JSON).content(unfreeze)
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:unfreeze"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.freezeId").value(freezeId));
        mvc.perform(post("/inventory/freezes/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"balanceId\":\"" + balanceId + "\"}")
                        .with(opaqueToken().authorities(new SimpleGrantedAuthority("wms:inventory:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].reasonCode").value("PACKAGE_DAMAGE"));
    }
}
