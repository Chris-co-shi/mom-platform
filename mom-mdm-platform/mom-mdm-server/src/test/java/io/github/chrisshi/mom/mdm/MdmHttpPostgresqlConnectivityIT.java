package io.github.chrisshi.mom.mdm;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OpaqueTokenRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * MDM 当前全部业务 Slice 的 HTTP 到 PostgreSQL 连通性验收。
 *
 * <p>该测试在真实 Spring Web 上下文中通过 MockMvc 进入安全过滤器、Controller、Application、
 * MyBatis-Plus Mapper、Flyway 后的 PostgreSQL，并串联工厂、仓储、位置、计量单位、物料分类和物料。
 * MockMvc 不打开真实网络端口，因此它属于进程内 L3 证据；打包 JAR、实际端口和容器启动由独立
 * {@code mdm-postgresql-smoke.sh} 验证。</p>
 *
 * <p>认证使用 Spring Security Test 注入的 Opaque Token 身份，验证方法授权与 CurrentActor 审计传播，
 * 但不会连接 Redis Token Store，也不声称覆盖真实令牌内省。Docker 或 PostgreSQL 不可用时测试显式跳过，
 * 发布验收不得把 skipped 解释为成功。每个测试独占清理后的业务数据，类本身无共享可变状态。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.test.context.SpringBootTest(
        classes = MomMdmApplication.class,
        webEnvironment = MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=true",
                "management.health.redis.enabled=false"
        })
class MdmHttpPostgresqlConnectivityIT {
    private static final String SCHEMA = "mom_mdm";
    private static final String ACTOR = "mdm-connectivity-actor";
    private static final String KILOGRAM_ID = "800000000000000210";
    private static final String GRAM_ID = "800000000000000211";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    /**
     * 将测试容器连接注入生产同构的 Schema、TCP Keepalive 与 ApplicationName 配置。
     *
     * @param registry Spring 测试动态属性注册器
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=mom-mdm-http-connectivity-it");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /** 为每次验收建立启用真实 SecurityFilterChain 的 MockMvc，并清理非种子主数据。 */
    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        jdbcTemplate.update("""
                TRUNCATE TABLE mdm_production_version,
                               mdm_routing_step, mdm_routing_revision, mdm_routing,
                               mdm_formula_item, mdm_formula_revision, mdm_formula
                """);
        jdbcTemplate.update("DELETE FROM mdm_material");
        jdbcTemplate.update("DELETE FROM mdm_uom_conversion_rule WHERE created_by <> 'flyway:uom-seed'");
        jdbcTemplate.update("DELETE FROM mdm_uom WHERE created_by <> 'flyway:uom-seed'");
        jdbcTemplate.update("DELETE FROM mdm_uom_category WHERE created_by <> 'flyway:uom-seed'");
        jdbcTemplate.update("DELETE FROM mdm_dimension WHERE created_by <> 'flyway:uom-seed'");
        jdbcTemplate.update("""
                TRUNCATE TABLE mdm_material_category, mdm_location, mdm_location_type,
                               mdm_warehouse_area, mdm_warehouse,
                               mdm_workstation, mdm_production_line, mdm_workshop, mdm_plant
                """);
    }

    /**
     * 串联当前 MDM 主数据并证明创建结果、关联过滤、单位换算与审计最终落入同一 PostgreSQL Schema。
     *
     * @throws Exception MockMvc 请求执行失败时由 JUnit 记录为测试错误
     */
    @Test
    void shouldConnectAllCurrentMdmSlicesThroughHttpAndPostgresql() throws Exception {
        String plantId = create("/plants", """
                {"code":"PLANT-E2E","name":"端到端工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        String workshopId = create("/workshops", """
                {"plantId":"%s","code":"WORKSHOP-E2E","name":"端到端车间","status":"ENABLED"}
                """.formatted(plantId));
        String productionLineId = create("/production-lines", """
                {"workshopId":"%s","code":"LINE-E2E","name":"端到端产线","status":"ENABLED"}
                """.formatted(workshopId));
        create("/workstations", """
                {"productionLineId":"%s","code":"STATION-E2E","name":"端到端工位","status":"ENABLED"}
                """.formatted(productionLineId));

        String warehouseId = create("/warehouses", """
                {"plantId":"%s","code":"WAREHOUSE-E2E","name":"端到端仓库","status":"ENABLED"}
                """.formatted(plantId));
        String warehouseAreaId = create("/warehouse-areas", """
                {"warehouseId":"%s","code":"AREA-E2E","name":"端到端库区","status":"ENABLED"}
                """.formatted(warehouseId));
        String locationTypeId = create("/location-types", """
                {"code":"TYPE-E2E","name":"端到端位置类型","status":"ENABLED"}
                """);
        String locationId = create("/locations", """
                {"code":"LOCATION-E2E","name":"端到端位置","plantId":"%s",
                 "warehouseAreaId":"%s","locationTypeId":"%s","status":"ENABLED"}
                """.formatted(plantId, warehouseAreaId, locationTypeId));

        String rootCategoryId = create("/material-categories", """
                {"code":"CATEGORY-ROOT-E2E","name":"端到端根分类","parentId":null,
                 "sort":10,"defaultShelfLifeDays":180,"status":"ENABLED"}
                """);
        String leafCategoryId = create("/material-categories", """
                {"code":"CATEGORY-LEAF-E2E","name":"端到端叶子分类","parentId":"%s",
                 "sort":20,"defaultShelfLifeDays":90,"defaultBatchTrackingPolicy":"REQUIRED","status":"ENABLED"}
                """.formatted(rootCategoryId));
        String materialId = create("/materials", """
                {"code":"MATERIAL-E2E","name":"端到端物料","categoryId":"%s",
                 "baseUomId":"%s","materialType":"RAW_MATERIAL",
                 "batchTrackingSource":"CATEGORY_DEFAULT","shelfLifeSource":"CATEGORY_DEFAULT","status":"ENABLED"}
                """.formatted(leafCategoryId, KILOGRAM_ID));

        mockMvc.perform(get("/material-references/{id}", materialId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(materialId))
                .andExpect(jsonPath("$.data.materialType").value("RAW_MATERIAL"))
                .andExpect(jsonPath("$.data.batchTrackingPolicy").value("REQUIRED"))
                .andExpect(jsonPath("$.data.revisionNo").value(1))
                .andExpect(jsonPath("$.data.availableForNewUse").value(true))
                .andExpect(jsonPath("$.data.invalidReason").isEmpty());

        performPost("/locations/search", """
                {"params":{"warehouseAreaId":"%s"},"pageNo":1,"pageSize":20}
                """.formatted(warehouseAreaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(locationId));

        performPost("/materials/search", """
                {"params":{"categoryId":"%s","baseUomId":"%s","status":"ENABLED"},
                 "pageNo":1,"pageSize":20}
                """.formatted(leafCategoryId, KILOGRAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(materialId))
                .andExpect(jsonPath("$.data.records[0].shelfLifeDays").value(90))
                .andExpect(jsonPath("$.data.records[0].materialType").value("RAW_MATERIAL"))
                .andExpect(jsonPath("$.data.records[0].batchTrackingPolicy").value("REQUIRED"))
                .andExpect(jsonPath("$.data.records[0].revisionNo").value(1));

        mockMvc.perform(put("/materials/{id}", materialId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"非法换基础单位","categoryId":"%s","baseUomId":"%s",
                                 "batchTrackingPolicy":"REQUIRED","shelfLifeDays":90,"version":0}
                                """.formatted(leafCategoryId, GRAM_ID)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/uoms/{id}", KILOGRAM_ID).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("kg"));
        performPost("/uom-conversions/convert", """
                {"value":"1000","sourceUomId":"%s","targetUomId":"%s"}
                """.formatted(GRAM_ID, KILOGRAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetValue").value("1"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_material WHERE id = ? AND category_id = ? AND base_uom_id = ?",
                Long.class, materialId, leafCategoryId, KILOGRAM_ID)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM mdm_material WHERE id = ?", String.class, materialId)).isEqualTo(ACTOR);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_location WHERE id = ? AND warehouse_area_id = ?",
                Long.class, locationId, warehouseAreaId)).isEqualTo(1L);

        mockMvc.perform(get("/material-references/{id}", "999999999999999999").with(actor()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/material-references/{id}", materialId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/material-references/{id}", materialId).with(opaqueToken()
                        .authorities(new SimpleGrantedAuthority("mdm:uom:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/materials/disable/{id}", materialId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/material-references/{id}", materialId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(false))
                .andExpect(jsonPath("$.data.invalidReason").value("MATERIAL_DISABLED"));
    }

    /** 新建工厂必须明确 IANA 时区，且返回权威时区供业务日期计算。 */
    @Test
    void plantMustRequireIanaTimeZone() throws Exception {
        performPost("/plants", """
                {"code":"PLANT-NO-ZONE","name":"无时区工厂","status":"ENABLED"}
                """).andExpect(status().isBadRequest());
        performPost("/plants", """
                {"code":"PLANT-OFFSET","name":"偏移量工厂","timeZoneId":"+08:00","status":"ENABLED"}
                """).andExpect(status().isBadRequest());
        String id = create("/plants", """
                {"code":"PLANT-ZONED","name":"有时区工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        mockMvc.perform(get("/plants/{id}", id).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeZoneId").value("Asia/Shanghai"));
    }

    /** 父工厂停用后，子级自身状态不变，但不得被新的制造业务引用。 */
    @Test
    void manufacturingResourceReferenceMustObserveAncestorStatus() throws Exception {
        String plantId = create("/plants", """
                {"code":"PLANT-REF","name":"引用工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        String workshopId = create("/workshops", """
                {"plantId":"%s","code":"WORKSHOP-REF","name":"引用车间","status":"ENABLED"}
                """.formatted(plantId));
        String lineId = create("/production-lines", """
                {"workshopId":"%s","code":"LINE-REF","name":"引用产线","status":"ENABLED"}
                """.formatted(workshopId));
        String stationId = create("/workstations", """
                {"productionLineId":"%s","code":"STATION-REF","name":"引用工位","status":"ENABLED"}
                """.formatted(lineId));
        mockMvc.perform(get("/manufacturing-resource-references/WORKSTATION/{id}", stationId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plantId").value(plantId))
                .andExpect(jsonPath("$.data.timeZoneId").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.availableForNewUse").value(true));

        mockMvc.perform(patch("/plants/disable/{id}", plantId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/manufacturing-resource-references/WORKSTATION/{id}", stationId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(false))
                .andExpect(jsonPath("$.data.invalidReason").value("PLANT_DISABLED"));
        performPost("/production-lines", """
                {"workshopId":"%s","code":"LINE-BLOCKED","name":"不得新建","status":"ENABLED"}
                """.formatted(workshopId)).andExpect(status().isConflict());
        mockMvc.perform(patch("/workstations/disable/{id}", stationId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/workstations/enable/{id}", stationId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/manufacturing-resource-references/UNKNOWN/{id}", stationId).with(actor()))
                .andExpect(status().isBadRequest());
    }

    /** 通用厂内位置不强制关联库区；关联仓储区域的静态位置随父仓库停用而不可新引用。 */
    @Test
    void locationReferenceMustDistinguishGenericAndWarehousePosition() throws Exception {
        String plantId = create("/plants", """
                {"code":"PLANT-LOC-REF","name":"位置工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        String warehouseId = create("/warehouses", """
                {"plantId":"%s","code":"WAREHOUSE-LOC-REF","name":"位置仓库","status":"ENABLED"}
                """.formatted(plantId));
        String areaId = create("/warehouse-areas", """
                {"warehouseId":"%s","code":"AREA-LOC-REF","name":"位置库区","status":"ENABLED"}
                """.formatted(warehouseId));
        String typeId = create("/location-types", """
                {"code":"TYPE-LOC-REF","name":"位置分类","status":"ENABLED"}
                """);
        String genericId = create("/locations", """
                {"plantId":"%s","warehouseAreaId":null,"locationTypeId":"%s",
                 "code":"GENERIC-LOC-REF","name":"通用位置","status":"ENABLED"}
                """.formatted(plantId, typeId));
        String storageId = create("/locations", """
                {"plantId":"%s","warehouseAreaId":"%s","locationTypeId":"%s",
                 "code":"STORAGE-LOC-REF","name":"仓储位置","status":"ENABLED"}
                """.formatted(plantId, areaId, typeId));
        mockMvc.perform(get("/location-references/{id}", genericId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warehouseAreaId").isEmpty())
                .andExpect(jsonPath("$.data.availableForNewUse").value(true));
        mockMvc.perform(patch("/warehouses/disable/{id}", warehouseId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/location-references/{id}", storageId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(false))
                .andExpect(jsonPath("$.data.invalidReason").value("WAREHOUSE_DISABLED"));
        mockMvc.perform(get("/warehouse-references/{id}", warehouseId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(false))
                .andExpect(jsonPath("$.data.invalidReason").value("WAREHOUSE_DISABLED"));
        mockMvc.perform(get("/warehouse-area-references/{id}", areaId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(false))
                .andExpect(jsonPath("$.data.invalidReason").value("WAREHOUSE_DISABLED"));
        mockMvc.perform(get("/location-references/{id}", genericId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(true));
        mockMvc.perform(patch("/warehouses/enable/{id}", warehouseId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/plants/disable/{id}", plantId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        performPost("/warehouse-areas", """
                {"warehouseId":"%s","code":"AREA-ANCESTOR-BLOCKED","name":"不得新建","status":"ENABLED"}
                """.formatted(warehouseId)).andExpect(status().isConflict());
    }

    /** DMC 原料进入配方草稿后只能发布不可变修订，不能回写已发布明细。 */
    @Test
    void formulaRevisionMustPublishAndRemainImmutable() throws Exception {
        String plantId = create("/plants", """
                {"code":"PLANT-FORMULA","name":"配液工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        String categoryId = create("/material-categories", """
                {"code":"CAT-FORMULA","name":"配方物料","parentId":null,"sort":1,
                 "defaultBatchTrackingPolicy":"REQUIRED","status":"ENABLED"}
                """);
        String dmcId = create("/materials", """
                {"code":"DMC-FORMULA","name":"碳酸二甲酯","categoryId":"%s","baseUomId":"%s",
                 "materialType":"RAW_MATERIAL","batchTrackingSource":"CATEGORY_DEFAULT",
                 "shelfLifeSource":"EXPLICIT","shelfLifeDays":180,"status":"ENABLED"}
                """.formatted(categoryId, KILOGRAM_ID));
        String outputId = create("/materials", """
                {"code":"ELECTROLYTE-FORMULA","name":"电解液","categoryId":"%s","baseUomId":"%s",
                 "materialType":"FINISHED_PRODUCT","batchTrackingSource":"CATEGORY_DEFAULT",
                 "shelfLifeSource":"EXPLICIT","shelfLifeDays":90,"status":"ENABLED"}
                """.formatted(categoryId, KILOGRAM_ID));
        performPost("/formulas", """
                {"code":"FORMULA-BAD-DIM","name":"错误量纲","plantId":"%s",
                 "outputMaterialId":"%s","baseBatchQuantity":"100","baseUomId":"%s",
                 "items":[{"sequenceNo":10,"materialId":"%s","quantity":"80","uomId":"800000000000000216"}]}
                """.formatted(plantId, outputId, KILOGRAM_ID, dmcId))
                .andExpect(status().isBadRequest());
        performPost("/formulas", """
                {"code":"FORMULA-NUMBER","name":"错误 JSON 数值","plantId":"%s",
                 "outputMaterialId":"%s","baseBatchQuantity":100,"baseUomId":"%s",
                 "items":[{"sequenceNo":10,"materialId":"%s","quantity":"80","uomId":"%s"}]}
                """.formatted(plantId, outputId, KILOGRAM_ID, dmcId, KILOGRAM_ID))
                .andExpect(status().isBadRequest());
        String response = performPost("/formulas", """
                {"code":"FORMULA-DMC-001","name":"DMC 配液配方","plantId":"%s",
                 "outputMaterialId":"%s","baseBatchQuantity":"100.000000000","baseUomId":"%s",
                 "items":[{"sequenceNo":10,"materialId":"%s","quantity":"80.000000000","uomId":"%s"}]}
                """.formatted(plantId, outputId, KILOGRAM_ID, dmcId, KILOGRAM_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revision.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.revision.revisionNo").value(1))
                .andExpect(jsonPath("$.data.items[0].materialId").value(dmcId))
                .andReturn().getResponse().getContentAsString();
        String revisionId = JsonPath.read(response, "$.data.revision.id");
        mockMvc.perform(patch("/formulas/revisions/release/{id}", revisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision.status").value("RELEASED"));
        mockMvc.perform(put("/formulas/revisions/{id}", revisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"lockVersion":1,"items":[{"sequenceNo":10,"materialId":"%s",
                                "quantity":"81.000000000","uomId":"%s"}]}
                                """.formatted(dmcId, KILOGRAM_ID)))
                .andExpect(status().isConflict());
        String formulaId = JsonPath.read(response, "$.data.formula.id");
        String nextResponse = performPost("/formulas/revisions/next/{id}".replace("{id}", formulaId), "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revision.revisionNo").value(2))
                .andExpect(jsonPath("$.data.revision.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String nextRevisionId = JsonPath.read(nextResponse, "$.data.revision.id");
        performPost("/formulas/search", "{\"params\":{},\"pageNo\":1,\"pageSize\":20}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].releasedRevisions[0].id").value(revisionId))
                .andExpect(jsonPath("$.data.records[0].releasedRevisions[0].revisionNo").value(1));
        mockMvc.perform(put("/formulas/revisions/{id}", nextRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"lockVersion":5,"items":[{"sequenceNo":10,"materialId":"%s",
                                "quantity":"81","uomId":"%s"}]}
                                """.formatted(dmcId, KILOGRAM_ID)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/formulas/revisions/{id}", revisionId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value("80.000000000"));
    }

    /** 成品标准入口的读取和写入权限由 MDM Resource Server 分别执行。 */
    @Test
    void finishedGoodsQualityEndpointsMustRejectMissingAuthority() throws Exception {
        String search = "{\"params\":{},\"pageNo\":1,\"pageSize\":20}";
        mockMvc.perform(post("/quality-specifications/search")
                        .with(opaqueToken().attributes(attributes -> attributes.put("sub", ACTOR))
                                .authorities(new SimpleGrantedAuthority("mdm:manufacturing-definition:read")))
                        .contentType(MediaType.APPLICATION_JSON).content(search))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/quality-specifications/search")
                        .with(opaqueToken().attributes(attributes -> attributes.put("sub", ACTOR))
                                .authorities(new SimpleGrantedAuthority("mdm:quality-definition:read")))
                        .contentType(MediaType.APPLICATION_JSON).content(search))
                .andExpect(status().isOk());
        mockMvc.perform(post("/quality-specifications")
                        .with(opaqueToken().attributes(attributes -> attributes.put("sub", ACTOR))
                                .authorities(new SimpleGrantedAuthority("mdm:quality-definition:read")))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":"NO-AUTH","name":"无权创建","plantId":"1","materialId":"2",
                         "inspectionUsage":"FINISHED_GOODS","items":[{"sequenceNo":10,
                         "characteristicId":"3","methodId":"4","uomId":"5",
                         "lowerLimit":"99","targetValue":"99.5","upperLimit":"100","mandatory":true}]}
                        """))
                .andExpect(status().isForbidden());
    }

    /** 工艺路线必须锁定同工厂的制造资源，发布后历史步骤保持不可变。 */
    @Test
    void routingRevisionMustRejectForeignPlantAndRemainImmutable() throws Exception {
        String plantId = create("/plants", """
                {"code":"PLANT-ROUTE","name":"路线工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        String workshopId = create("/workshops", """
                {"plantId":"%s","code":"SHOP-ROUTE","name":"路线车间","status":"ENABLED"}
                """.formatted(plantId));
        String lineId = create("/production-lines", """
                {"workshopId":"%s","code":"LINE-ROUTE","name":"配液线","status":"ENABLED"}
                """.formatted(workshopId));
        String stationId = create("/workstations", """
                {"productionLineId":"%s","code":"STATION-ROUTE","name":"配液工位","status":"ENABLED"}
                """.formatted(lineId));
        String foreignPlantId = create("/plants", """
                {"code":"PLANT-ROUTE-OTHER","name":"其他工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        performPost("/routings", """
                {"code":"ROUTE-BAD","name":"跨厂路线","plantId":"%s",
                 "steps":[{"sequenceNo":10,"operationCode":"MIX","name":"配液",
                           "resourceType":"PRODUCTION_LINE","resourceId":"%s"}]}
                """.formatted(foreignPlantId, lineId)).andExpect(status().isBadRequest());
        String response = performPost("/routings", """
                {"code":"ROUTE-DMC","name":"DMC 配液路线","plantId":"%s",
                 "steps":[{"sequenceNo":10,"operationCode":"MIX","name":"配液",
                           "resourceType":"PRODUCTION_LINE","resourceId":"%s"}]}
                """.formatted(plantId, lineId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revision.revisionNo").value(1))
                .andReturn().getResponse().getContentAsString();
        String revisionId = JsonPath.read(response, "$.data.revision.id");
        mockMvc.perform(patch("/routings/revisions/release/{id}", revisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision.status").value("RELEASED"));
        mockMvc.perform(put("/routings/revisions/{id}", revisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"lockVersion":1,"steps":[{"sequenceNo":10,"operationCode":"MIX",
                                "name":"变更配液","resourceType":"PRODUCTION_LINE","resourceId":"%s"}]}
                                """.formatted(lineId)))
                .andExpect(status().isConflict());
        String routingId = JsonPath.read(response, "$.data.routing.id");
        String nextResponse = performPost("/routings/revisions/next/" + routingId, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.revision.revisionNo").value(2))
                .andReturn().getResponse().getContentAsString();
        String nextRevisionId = JsonPath.read(nextResponse, "$.data.revision.id");
        performPost("/routings/search", "{\"params\":{},\"pageNo\":1,\"pageSize\":20}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].releasedRevisions[0].id").value(revisionId))
                .andExpect(jsonPath("$.data.records[0].releasedRevisions[0].revisionNo").value(1));
        mockMvc.perform(put("/routings/revisions/{id}", nextRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"lockVersion":0,"steps":[{"sequenceNo":10,"operationCode":"MIX",
                                "name":"配液工位操作","resourceType":"WORKSTATION","resourceId":"%s"}]}
                                """.formatted(stationId)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/production-lines/disable/{id}", lineId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/routings/revisions/release/{id}", nextRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/production-lines/enable/{id}", lineId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/routings/revisions/release/{id}", nextRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":1}"))
                .andExpect(status().isOk());
        performPost("/routings/search", "{\"params\":{},\"pageNo\":1,\"pageSize\":20}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].releasedRevisions[0].id").value(revisionId))
                .andExpect(jsonPath("$.data.records[0].releasedRevisions[1].id").value(nextRevisionId));
    }

    /** 生产版本只绑定已发布定义，同工厂同产出物料的已发布有效期不得重叠。 */
    @Test
    void productionVersionMustPublishWithoutOverlapAndExposeStableReference() throws Exception {
        String plantId = create("/plants", """
                {"code":"PLANT-PV","name":"生产版本工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        String workshopId = create("/workshops", """
                {"plantId":"%s","code":"SHOP-PV","name":"生产版本车间","status":"ENABLED"}
                """.formatted(plantId));
        String lineId = create("/production-lines", """
                {"workshopId":"%s","code":"LINE-PV","name":"生产版本产线","status":"ENABLED"}
                """.formatted(workshopId));
        String categoryId = create("/material-categories", """
                {"code":"CAT-PV","name":"生产版本物料","parentId":null,"sort":1,
                 "defaultBatchTrackingPolicy":"REQUIRED","status":"ENABLED"}
                """);
        String inputId = create("/materials", """
                {"code":"DMC-PV","name":"DMC","categoryId":"%s","baseUomId":"%s",
                 "materialType":"RAW_MATERIAL","batchTrackingSource":"CATEGORY_DEFAULT",
                 "shelfLifeSource":"EXPLICIT","shelfLifeDays":180,"status":"ENABLED"}
                """.formatted(categoryId, KILOGRAM_ID));
        String outputId = create("/materials", """
                {"code":"OUTPUT-PV","name":"电解液","categoryId":"%s","baseUomId":"%s",
                 "materialType":"FINISHED_PRODUCT","batchTrackingSource":"CATEGORY_DEFAULT",
                 "shelfLifeSource":"EXPLICIT","shelfLifeDays":90,"status":"ENABLED"}
                """.formatted(categoryId, KILOGRAM_ID));
        String formulaResponse = performPost("/formulas", """
                {"code":"FORMULA-PV","name":"生产版本配方","plantId":"%s",
                 "outputMaterialId":"%s","baseBatchQuantity":"100","baseUomId":"%s",
                 "items":[{"sequenceNo":10,"materialId":"%s","quantity":"80","uomId":"%s"}]}
                """.formatted(plantId, outputId, KILOGRAM_ID, inputId, KILOGRAM_ID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String formulaRevisionId = JsonPath.read(formulaResponse, "$.data.revision.id");
        mockMvc.perform(patch("/formulas/revisions/release/{id}", formulaRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk());
        String routingResponse = performPost("/routings", """
                {"code":"ROUTING-PV","name":"生产版本路线","plantId":"%s",
                 "steps":[{"sequenceNo":10,"operationCode":"MIX","name":"配液",
                           "resourceType":"PRODUCTION_LINE","resourceId":"%s"}]}
                """.formatted(plantId, lineId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String routingRevisionId = JsonPath.read(routingResponse, "$.data.revision.id");
        mockMvc.perform(patch("/routings/revisions/release/{id}", routingRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk());

        String pvResponse = performPost("/production-versions", """
                {"code":"PV-DMC-001","name":"DMC 生产版本","businessVersion":"1.0",
                 "plantId":"%s","outputMaterialId":"%s","formulaRevisionId":"%s",
                 "routingRevisionId":"%s","validFrom":"2026-01-01","validTo":"2027-01-01"}
                """.formatted(plantId, outputId, formulaRevisionId, routingRevisionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String productionVersionId = JsonPath.read(pvResponse, "$.data.id");
        mockMvc.perform(patch("/production-versions/release/{id}", productionVersionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"));
        String secondOutputId = create("/materials", """
                {"code":"OUTPUT-PV-2","name":"第二种电解液","categoryId":"%s","baseUomId":"%s",
                 "materialType":"FINISHED_PRODUCT","batchTrackingSource":"CATEGORY_DEFAULT",
                 "shelfLifeSource":"EXPLICIT","shelfLifeDays":90,"status":"ENABLED"}
                """.formatted(categoryId, KILOGRAM_ID));
        String secondFormulaResponse = performPost("/formulas", """
                {"code":"FORMULA-PV-2","name":"第二产品配方","plantId":"%s",
                 "outputMaterialId":"%s","baseBatchQuantity":"100","baseUomId":"%s",
                 "items":[{"sequenceNo":10,"materialId":"%s","quantity":"75","uomId":"%s"}]}
                """.formatted(plantId, secondOutputId, KILOGRAM_ID, inputId, KILOGRAM_ID))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String secondFormulaRevisionId = JsonPath.read(secondFormulaResponse, "$.data.revision.id");
        mockMvc.perform(patch("/formulas/revisions/release/{id}", secondFormulaRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk());
        String secondRoutingResponse = performPost("/routings", """
                {"code":"ROUTING-PV-2","name":"第二产品路线","plantId":"%s",
                 "steps":[{"sequenceNo":10,"operationCode":"MIX-2","name":"第二配液",
                           "resourceType":"PRODUCTION_LINE","resourceId":"%s"}]}
                """.formatted(plantId, lineId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String secondRoutingRevisionId = JsonPath.read(secondRoutingResponse, "$.data.revision.id");
        mockMvc.perform(patch("/routings/revisions/release/{id}", secondRoutingRevisionId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk());
        performPost("/production-versions", """
                {"code":"PV-WRONG-PAIR","name":"错误配方引用","businessVersion":"1.0",
                 "plantId":"%s","outputMaterialId":"%s","formulaRevisionId":"%s",
                 "routingRevisionId":"%s","validFrom":"2026-01-01","validTo":"2027-01-01"}
                """.formatted(plantId, secondOutputId, formulaRevisionId, secondRoutingRevisionId))
                .andExpect(status().isBadRequest());
        String secondPvId = create("/production-versions", """
                {"code":"PV-DMC-002","name":"第二产品版本","businessVersion":"1.0",
                 "plantId":"%s","outputMaterialId":"%s","formulaRevisionId":"%s",
                 "routingRevisionId":"%s","validFrom":"2026-01-01","validTo":"2027-01-01"}
                """.formatted(plantId, secondOutputId, secondFormulaRevisionId, secondRoutingRevisionId));
        mockMvc.perform(patch("/production-versions/release/{id}", secondPvId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"));
        performPost("/production-versions", """
                {"code":"PV-DMC-OVERLAP","name":"重叠版本","businessVersion":"2.0",
                 "plantId":"%s","outputMaterialId":"%s","formulaRevisionId":"%s",
                 "routingRevisionId":"%s","validFrom":"2026-12-01","validTo":null}
                """.formatted(plantId, outputId, formulaRevisionId, routingRevisionId))
                .andExpect(status().isCreated());
        String overlapId = jdbcTemplate.queryForObject(
                "SELECT id FROM mdm_production_version WHERE code='PV-DMC-OVERLAP'", String.class);
        mockMvc.perform(patch("/production-versions/release/{id}", overlapId).with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/production-version-references/{id}", productionVersionId).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(true))
                .andExpect(jsonPath("$.data.businessVersion").value("1.0"));
    }

    /**
     * 验证默认 Resource Server 对匿名请求返回 401，并由各业务域方法权限区分读写请求。
     *
     * @throws Exception MockMvc 请求执行失败时由 JUnit 记录为测试错误
     */
    @Test
    void shouldEnforceAuthenticationAndDomainReadWriteAuthorities() throws Exception {
        mockMvc.perform(get("/location-references/{id}", KILOGRAM_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/location-references/{id}", KILOGRAM_ID).with(opaqueToken()
                        .authorities(new SimpleGrantedAuthority("mdm:warehouse:read"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/uom-references/{id}", KILOGRAM_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/uom-references/{id}", KILOGRAM_ID).with(readOnlyActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableForNewUse").value(true));
        mockMvc.perform(get("/uom-references/{id}", "999999999999999999").with(readOnlyActor()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/uoms/{id}", KILOGRAM_ID))
                .andExpect(status().isUnauthorized());

        String plantId = create("/plants", """
                {"code":"PLANT-AUTH","name":"权限测试工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                """);
        mockMvc.perform(get("/plants/{id}", plantId).with(readOnlyActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(plantId));

        mockMvc.perform(post("/plants")
                        .with(readOnlyActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FORBIDDEN-PLANT","name":"禁止写入工厂","timeZoneId":"Asia/Shanghai","status":"ENABLED"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/dimensions")
                        .with(readOnlyActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FORBIDDEN-DIMENSION","name":"禁止写入量纲",
                                 "timeExponent":0,"lengthExponent":0,"massExponent":0,
                                 "electricCurrentExponent":0,"temperatureExponent":0,
                                 "amountExponent":0,"luminousIntensityExponent":0,"status":"ENABLED"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_dimension WHERE code = 'FORBIDDEN-DIMENSION'",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_plant WHERE code = 'FORBIDDEN-PLANT'",
                Long.class)).isZero();
    }

    /**
     * 真实 HTTP 边界按请求语言解析数据库译文；同一 JVM 更新译文后下次请求立即可见。
     * 测试结束恢复种子译文，避免影响同容器中的其他用例。
     */
    @Test
    void mdmErrorMustFollowAcceptLanguageAndObserveUpdatedTranslation() throws Exception {
        String missingId = "999999999999999999";
        mockMvc.perform(get("/i18n/runtime/bundles")
                        .param("locale", "zh-CN"))
                .andExpect(status().isOk());
        // 按当前 i18n 公开读取契约，匿名 GET 文案列表允许读取；写入仍受权限保护。
        mockMvc.perform(get("/i18n/messages"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/uoms/{id}", missingId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/uoms/{id}", missingId).with(readOnlyActor())
                        .header("Accept-Language", "zh-CN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("mdm.resource_not_found"))
                .andExpect(jsonPath("$.message").value("Uom不存在"))
                .andExpect(jsonPath("$.namespace").doesNotExist())
                .andExpect(jsonPath("$.messageKey").doesNotExist());
        mockMvc.perform(get("/uoms/{id}", missingId).with(readOnlyActor())
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("The Uom does not exist"));

        try {
            jdbcTemplate.update("UPDATE mdm_i18n_translation SET message_text = ? WHERE id = ?",
                    "Unit {0} is absent", "9720000000000000002");
            mockMvc.perform(get("/uoms/{id}", missingId).with(readOnlyActor())
                            .header("Accept-Language", "en-US"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Unit Uom is absent"));
        } finally {
            jdbcTemplate.update("UPDATE mdm_i18n_translation SET message_text = ? WHERE id = ?",
                    "The {0} does not exist", "9720000000000000002");
        }
    }

    /**
     * 执行创建请求并从统一 Result 信封提取服务端生成的 String ID。
     *
     * @param path 创建端点
     * @param json 合法 JSON 请求体
     * @return 响应 {@code data.id}
     * @throws Exception HTTP 执行或响应解析失败
     */
    private String create(String path, String json) throws Exception {
        String response = performPost(path, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    /**
     * 使用同一 Opaque Token Actor 执行 JSON POST，确保业务写与分页均经过安全过滤器。
     *
     * @param path 请求路径
     * @param json JSON 请求体
     * @return 可继续声明 HTTP 断言的结果
     * @throws Exception 请求执行失败
     */
    private org.springframework.test.web.servlet.ResultActions performPost(String path, String json) throws Exception {
        return mockMvc.perform(post(path).with(actor()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** 构造具备当前全部 MDM 业务域读写权限的稳定测试身份。 */
    private static OpaqueTokenRequestPostProcessor actor() {
        return opaqueToken()
                .attributes(attributes -> attributes.put("sub", ACTOR))
                .authorities(
                        new SimpleGrantedAuthority("mdm:factory:read"),
                        new SimpleGrantedAuthority("mdm:factory:write"),
                        new SimpleGrantedAuthority("mdm:warehouse:read"),
                        new SimpleGrantedAuthority("mdm:warehouse:write"),
                        new SimpleGrantedAuthority("mdm:location:read"),
                        new SimpleGrantedAuthority("mdm:location:write"),
                        new SimpleGrantedAuthority("mdm:material:read"),
                        new SimpleGrantedAuthority("mdm:material:write"),
                        new SimpleGrantedAuthority("mdm:manufacturing-definition:read"),
                        new SimpleGrantedAuthority("mdm:manufacturing-definition:write"),
                        new SimpleGrantedAuthority("mdm:uom:read"),
                        new SimpleGrantedAuthority("mdm:uom:write"));
    }

    /** 构造只有当前全部 MDM 业务域读取权限的身份，用于证明读请求可用、写请求返回 403。 */
    private static OpaqueTokenRequestPostProcessor readOnlyActor() {
        return opaqueToken()
                .attributes(attributes -> attributes.put("sub", ACTOR + "-read-only"))
                .authorities(
                        new SimpleGrantedAuthority("mdm:factory:read"),
                        new SimpleGrantedAuthority("mdm:warehouse:read"),
                        new SimpleGrantedAuthority("mdm:location:read"),
                        new SimpleGrantedAuthority("mdm:material:read"),
                        new SimpleGrantedAuthority("mdm:uom:read"));
    }
}
