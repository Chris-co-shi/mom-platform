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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        String plantId = create("/api/mdm/plants", """
                {"code":"PLANT-E2E","name":"端到端工厂","status":"ENABLED"}
                """);
        String workshopId = create("/api/mdm/workshops", """
                {"plantId":"%s","code":"WORKSHOP-E2E","name":"端到端车间","status":"ENABLED"}
                """.formatted(plantId));
        String productionLineId = create("/api/mdm/production-lines", """
                {"workshopId":"%s","code":"LINE-E2E","name":"端到端产线","status":"ENABLED"}
                """.formatted(workshopId));
        create("/api/mdm/workstations", """
                {"productionLineId":"%s","code":"STATION-E2E","name":"端到端工位","status":"ENABLED"}
                """.formatted(productionLineId));

        String warehouseId = create("/api/mdm/warehouses", """
                {"plantId":"%s","code":"WAREHOUSE-E2E","name":"端到端仓库","status":"ENABLED"}
                """.formatted(plantId));
        String warehouseAreaId = create("/api/mdm/warehouse-areas", """
                {"warehouseId":"%s","code":"AREA-E2E","name":"端到端库区","status":"ENABLED"}
                """.formatted(warehouseId));
        String locationTypeId = create("/api/mdm/location-types", """
                {"code":"TYPE-E2E","name":"端到端位置类型","status":"ENABLED"}
                """);
        String locationId = create("/api/mdm/locations", """
                {"code":"LOCATION-E2E","name":"端到端位置","plantId":"%s",
                 "warehouseAreaId":"%s","locationTypeId":"%s","status":"ENABLED"}
                """.formatted(plantId, warehouseAreaId, locationTypeId));

        String rootCategoryId = create("/api/mdm/material-categories", """
                {"code":"CATEGORY-ROOT-E2E","name":"端到端根分类","parentId":null,
                 "sort":10,"defaultShelfLifeDays":180,"status":"ENABLED"}
                """);
        String leafCategoryId = create("/api/mdm/material-categories", """
                {"code":"CATEGORY-LEAF-E2E","name":"端到端叶子分类","parentId":"%s",
                 "sort":20,"defaultShelfLifeDays":90,"status":"ENABLED"}
                """.formatted(rootCategoryId));
        String materialId = create("/api/mdm/materials", """
                {"code":"MATERIAL-E2E","name":"端到端物料","categoryId":"%s",
                 "baseUomId":"%s","shelfLifeSource":"CATEGORY_DEFAULT","status":"ENABLED"}
                """.formatted(leafCategoryId, KILOGRAM_ID));

        performPost("/api/mdm/locations/search", """
                {"params":{"warehouseAreaId":"%s"},"pageNo":1,"pageSize":20}
                """.formatted(warehouseAreaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(locationId));

        performPost("/api/mdm/materials/search", """
                {"params":{"categoryId":"%s","baseUomId":"%s","status":"ENABLED"},
                 "pageNo":1,"pageSize":20}
                """.formatted(leafCategoryId, KILOGRAM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(materialId))
                .andExpect(jsonPath("$.data.records[0].shelfLifeDays").value(90));

        mockMvc.perform(get("/api/mdm/uoms/{id}", KILOGRAM_ID).with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("kg"));
        performPost("/api/mdm/uom-conversions/convert", """
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
    }

    /**
     * 验证默认 Resource Server 对匿名请求返回 401，并由各业务域方法权限区分读写请求。
     *
     * @throws Exception MockMvc 请求执行失败时由 JUnit 记录为测试错误
     */
    @Test
    void shouldEnforceAuthenticationAndDomainReadWriteAuthorities() throws Exception {
        mockMvc.perform(get("/api/mdm/uoms/{id}", KILOGRAM_ID))
                .andExpect(status().isUnauthorized());

        String plantId = create("/api/mdm/plants", """
                {"code":"PLANT-AUTH","name":"权限测试工厂","status":"ENABLED"}
                """);
        mockMvc.perform(get("/api/mdm/plants/{id}", plantId).with(readOnlyActor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(plantId));

        mockMvc.perform(post("/api/mdm/plants")
                        .with(readOnlyActor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"FORBIDDEN-PLANT","name":"禁止写入工厂","status":"ENABLED"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/mdm/dimensions")
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
