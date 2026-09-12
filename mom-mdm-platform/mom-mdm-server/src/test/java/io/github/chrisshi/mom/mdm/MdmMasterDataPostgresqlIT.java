package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.application.FactoryStructureApplication;
import io.github.chrisshi.mom.mdm.application.LocationMasterDataApplication;
import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataRules;
import io.github.chrisshi.mom.mdm.application.WarehouseStructureApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 第一组 MDM 主数据的真实 PostgreSQL 集成验收。
 *
 * <p>该测试执行真实 Flyway、MyBatis-Plus、审计填充、分页、唯一约束、父级校验和乐观锁。它不启动
 * Nacos、Redis、MQ 或 Seata；Docker 不可用时由 Testcontainers 显式跳过，不能将跳过描述为通过。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomMdmApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
@Import(MdmMasterDataPostgresqlIT.TestActorConfiguration.class)
class MdmMasterDataPostgresqlIT {
    private static final String SCHEMA = "mom_mdm";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired private FactoryStructureApplication factoryApplication;
    @Autowired private WarehouseStructureApplication warehouseApplication;
    @Autowired private LocationMasterDataApplication locationApplication;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 注入隔离 PostgreSQL 连接并保持生产 currentSchema、Keepalive 与 ApplicationName 约束。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=mom-mdm-master-data-it");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /** 每个测试清理本 Slice 的普通主数据；没有物理外键，因此不依赖级联顺序。 */
    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("""
                TRUNCATE TABLE mdm_location, mdm_location_type, mdm_warehouse_area, mdm_warehouse,
                               mdm_workstation, mdm_production_line, mdm_workshop, mdm_plant
                """);
    }

    /** 验证 V102 表、命名唯一约束、状态/版本约束和无物理外键策略。 */
    @Test
    void migrationMustCreateEightMasterTablesAndDatabaseConstraints() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT version FROM flyway_schema_history
                 WHERE success = true ORDER BY installed_rank DESC LIMIT 1
                """, String.class)).isEqualTo("102");
        assertThat(jdbcTemplate.queryForObject("select current_schema()", String.class)).isEqualTo(SCHEMA);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema = ? AND table_name IN (
                   'mdm_plant', 'mdm_workshop', 'mdm_production_line', 'mdm_workstation',
                   'mdm_warehouse', 'mdm_warehouse_area', 'mdm_location_type', 'mdm_location')
                """, Long.class, SCHEMA)).isEqualTo(8L);
        assertThat(jdbcTemplate.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                 WHERE table_schema = ? AND constraint_type = 'UNIQUE'
                   AND constraint_name LIKE 'uk_mdm_%'
                 ORDER BY constraint_name
                """, String.class, SCHEMA)).containsExactlyInAnyOrder(
                "uk_mdm_plant_code", "uk_mdm_workshop_plant_code",
                "uk_mdm_production_line_workshop_code", "uk_mdm_workstation_line_code",
                "uk_mdm_warehouse_plant_code", "uk_mdm_warehouse_area_warehouse_code",
                "uk_mdm_location_type_code", "uk_mdm_location_plant_code");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_schema = ? AND constraint_type = 'FOREIGN KEY'
                """, Long.class, SCHEMA)).isZero();
    }

    /** 覆盖正常层级创建、平台唯一 Code、同父级冲突、不同父级同 Code 和父级不存在。 */
    @Test
    void factoryAndWarehouseHierarchyMustEnforceParentScopedCodes() {
        var plantA = factoryApplication.createPlant("P-A", "工厂甲", "Plant A", MdmMasterDataRules.ENABLED);
        var plantB = factoryApplication.createPlant("P-B", "工厂乙", null, MdmMasterDataRules.ENABLED);
        assertCodeConflict(() -> factoryApplication.createPlant("P-A", "重复工厂", null, MdmMasterDataRules.ENABLED));

        var workshopA = factoryApplication.createWorkshop(plantA.id(), "WS-01", "一车间", null, MdmMasterDataRules.ENABLED);
        assertCodeConflict(() -> factoryApplication.createWorkshop(plantA.id(), "WS-01", "重复车间", null, MdmMasterDataRules.ENABLED));
        var workshopB = factoryApplication.createWorkshop(plantB.id(), "WS-01", "另一工厂一车间", null, MdmMasterDataRules.ENABLED);
        assertThat(workshopB.code()).isEqualTo(workshopA.code());
        assertNotFound(() -> factoryApplication.createWorkshop("missing", "WS-X", "无父级", null, MdmMasterDataRules.ENABLED));

        var line = factoryApplication.createProductionLine(workshopA.id(), "LINE-1", "一号线", null, MdmMasterDataRules.ENABLED);
        var station = factoryApplication.createWorkstation(line.id(), "ST-1", "一号工位", null, MdmMasterDataRules.ENABLED);
        var warehouse = warehouseApplication.createWarehouse(plantA.id(), "WH-1", "一号仓", null, MdmMasterDataRules.ENABLED);
        var area = warehouseApplication.createWarehouseArea(warehouse.id(), "AREA-1", "一区", null, MdmMasterDataRules.ENABLED);

        assertThat(station.productionLineId()).isEqualTo(line.id());
        assertThat(area.warehouseId()).isEqualTo(warehouse.id());
        assertNotFound(() -> factoryApplication.createProductionLine("missing", "LINE-X", "无父级", null, MdmMasterDataRules.ENABLED));
        assertNotFound(() -> warehouseApplication.createWarehouse("missing", "WH-X", "无父级", null, MdmMasterDataRules.ENABLED));
    }

    /** 覆盖 Location 无仓储区域、合法区域、跨 Plant 非法区域以及动态 LocationType 引用。 */
    @Test
    void locationMustAllowOptionalAreaAndValidateCompleteWarehousePlantChain() {
        var plantA = factoryApplication.createPlant("P-A", "工厂甲", null, MdmMasterDataRules.ENABLED);
        var plantB = factoryApplication.createPlant("P-B", "工厂乙", null, MdmMasterDataRules.ENABLED);
        var type = locationApplication.createLocationType("BUFFER", "缓存位置", null, MdmMasterDataRules.ENABLED);
        var warehouse = warehouseApplication.createWarehouse(plantA.id(), "WH-1", "一号仓", null, MdmMasterDataRules.ENABLED);
        var area = warehouseApplication.createWarehouseArea(warehouse.id(), "AREA-1", "一区", null, MdmMasterDataRules.ENABLED);
        var anotherArea = warehouseApplication.createWarehouseArea(
                warehouse.id(), "AREA-2", "二区", null, MdmMasterDataRules.ENABLED);

        var direct = locationApplication.createLocation(plantA.id(), null, type.id(), "LOC-1", "线边位置", null,
                MdmMasterDataRules.ENABLED);
        var storage = locationApplication.createLocation(plantA.id(), area.id(), type.id(), "LOC-2", "仓储位置", null,
                MdmMasterDataRules.ENABLED);
        locationApplication.createLocation(plantA.id(), anotherArea.id(), type.id(), "LOC-3", "二区位置", null,
                MdmMasterDataRules.ENABLED);

        assertThat(direct.warehouseAreaId()).isNull();
        assertThat(storage.warehouseAreaId()).isEqualTo(area.id());
        var areaPage = locationApplication.pageLocations(plantA.id(), area.id(), 1, 20);
        assertThat(areaPage.records()).extracting(record -> record.id()).containsExactly(storage.id());
        assertThat(areaPage.total()).isEqualTo(1);
        assertThatThrownBy(() -> locationApplication.createLocation(plantB.id(), area.id(), type.id(), "LOC-X",
                "跨工厂错误位置", null, MdmMasterDataRules.ENABLED))
                .isInstanceOfSatisfying(MdmException.class,
                        exception -> assertThat(exception.code()).isEqualTo("mdm.invalid_reference"));
        assertNotFound(() -> locationApplication.createLocation(plantA.id(), null, "missing", "LOC-Y",
                "无类型位置", null, MdmMasterDataRules.ENABLED));
    }

    /** 覆盖所有层级在父级停用时禁止创建或启用直接子级，且不引入隐式级联状态变更。 */
    @Test
    void disabledParentMustBlockCreatingAndEnablingDirectChildren() {
        var plantCreated = factoryApplication.createPlant("P-A", "工厂甲", null, MdmMasterDataRules.ENABLED);
        var workshopCreated = factoryApplication.createWorkshop(
                plantCreated.id(), "WS-1", "一车间", null, MdmMasterDataRules.ENABLED);

        var plantDisabledForWorkshop = factoryApplication.disablePlant(plantCreated.id(), plantCreated.version());
        assertParentDisabled(() -> factoryApplication.createWorkshop(
                plantDisabledForWorkshop.id(), "WS-2", "二车间", null, MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> factoryApplication.enableWorkshop(workshopCreated.id(), workshopCreated.version()));

        var plantEnabledForWorkshop = factoryApplication.enablePlant(
                plantDisabledForWorkshop.id(), plantDisabledForWorkshop.version());
        var lineCreated = factoryApplication.createProductionLine(
                workshopCreated.id(), "LINE-1", "一号线", null, MdmMasterDataRules.DISABLED);
        var workshopDisabledForLine = factoryApplication.disableWorkshop(
                workshopCreated.id(), workshopCreated.version());
        assertParentDisabled(() -> factoryApplication.createProductionLine(
                workshopDisabledForLine.id(), "LINE-2", "二号线", null, MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> factoryApplication.enableProductionLine(lineCreated.id(), lineCreated.version()));

        factoryApplication.enableWorkshop(workshopDisabledForLine.id(), workshopDisabledForLine.version());
        var lineEnabledForStation = factoryApplication.enableProductionLine(lineCreated.id(), lineCreated.version());
        var workstation = factoryApplication.createWorkstation(
                lineEnabledForStation.id(), "ST-1", "一号工位", null, MdmMasterDataRules.DISABLED);
        var lineDisabledForStation = factoryApplication.disableProductionLine(
                lineEnabledForStation.id(), lineEnabledForStation.version());
        assertParentDisabled(() -> factoryApplication.createWorkstation(
                lineDisabledForStation.id(), "ST-2", "二号工位", null, MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> factoryApplication.enableWorkstation(workstation.id(), workstation.version()));

        var warehouseCreated = warehouseApplication.createWarehouse(
                plantEnabledForWorkshop.id(), "WH-1", "一号仓", null, MdmMasterDataRules.DISABLED);
        var plantDisabledForWarehouse = factoryApplication.disablePlant(
                plantEnabledForWorkshop.id(), plantEnabledForWorkshop.version());
        assertParentDisabled(() -> warehouseApplication.createWarehouse(
                plantDisabledForWarehouse.id(), "WH-2", "二号仓", null, MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> warehouseApplication.enableWarehouse(
                warehouseCreated.id(), warehouseCreated.version()));

        var plantEnabledForWarehouse = factoryApplication.enablePlant(
                plantDisabledForWarehouse.id(), plantDisabledForWarehouse.version());
        var warehouseEnabledForArea = warehouseApplication.enableWarehouse(
                warehouseCreated.id(), warehouseCreated.version());
        var areaCreated = warehouseApplication.createWarehouseArea(
                warehouseEnabledForArea.id(), "AREA-1", "一区", null, MdmMasterDataRules.DISABLED);
        var warehouseDisabledForArea = warehouseApplication.disableWarehouse(
                warehouseEnabledForArea.id(), warehouseEnabledForArea.version());
        assertParentDisabled(() -> warehouseApplication.createWarehouseArea(
                warehouseDisabledForArea.id(), "AREA-2", "二区", null, MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> warehouseApplication.enableWarehouseArea(areaCreated.id(), areaCreated.version()));

        warehouseApplication.enableWarehouse(warehouseDisabledForArea.id(), warehouseDisabledForArea.version());
        var areaEnabledForLocation = warehouseApplication.enableWarehouseArea(
                areaCreated.id(), areaCreated.version());
        var type = locationApplication.createLocationType(
                "BUFFER", "缓存位置", null, MdmMasterDataRules.ENABLED);
        var directLocation = locationApplication.createLocation(
                plantEnabledForWarehouse.id(), null, type.id(), "LOC-1", "线边位置", null,
                MdmMasterDataRules.DISABLED);
        var areaLocation = locationApplication.createLocation(
                plantEnabledForWarehouse.id(), areaEnabledForLocation.id(), type.id(), "LOC-2", "仓储位置", null,
                MdmMasterDataRules.DISABLED);

        var plantDisabledForLocation = factoryApplication.disablePlant(
                plantEnabledForWarehouse.id(), plantEnabledForWarehouse.version());
        assertParentDisabled(() -> locationApplication.createLocation(
                plantDisabledForLocation.id(), null, type.id(), "LOC-3", "新位置", null,
                MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> locationApplication.enableLocation(directLocation.id(), directLocation.version()));

        var plantEnabledForLocation = factoryApplication.enablePlant(
                plantDisabledForLocation.id(), plantDisabledForLocation.version());
        var areaDisabledForLocation = warehouseApplication.disableWarehouseArea(
                areaEnabledForLocation.id(), areaEnabledForLocation.version());
        assertParentDisabled(() -> locationApplication.createLocation(
                plantEnabledForLocation.id(), areaDisabledForLocation.id(), type.id(), "LOC-4", "新仓储位置", null,
                MdmMasterDataRules.ENABLED));
        assertParentDisabled(() -> locationApplication.enableLocation(areaLocation.id(), areaLocation.version()));
    }

    /** 覆盖显式启停、更新不改变 Code、乐观锁冲突以及 PageResult 统一转换。 */
    @Test
    void lifecycleUpdateAndPagingMustRemainExplicitAndStable() {
        var plant = factoryApplication.createPlant("P-A", "旧名称", null, MdmMasterDataRules.ENABLED);
        var updated = factoryApplication.updatePlant(plant.id(), "新名称", "New Name", plant.version());
        assertThat(updated.code()).isEqualTo("P-A");
        assertThat(updated.nameZh()).isEqualTo("新名称");
        assertThat(updated.version()).isEqualTo(1L);

        var disabled = factoryApplication.disablePlant(plant.id(), updated.version());
        assertThat(disabled.status()).isEqualTo(MdmMasterDataRules.DISABLED);
        var enabled = factoryApplication.enablePlant(plant.id(), disabled.version());
        assertThat(enabled.status()).isEqualTo(MdmMasterDataRules.ENABLED);
        assertThatThrownBy(() -> factoryApplication.updatePlant(plant.id(), "过期更新", null, plant.version()))
                .isInstanceOfSatisfying(MdmException.class,
                        exception -> assertThat(exception.code()).isEqualTo("mdm.version_conflict"));

        factoryApplication.createPlant("P-B", "工厂乙", null, MdmMasterDataRules.ENABLED);
        var page = factoryApplication.pagePlants(1, 1);
        assertThat(page.records()).hasSize(1);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    private static void assertCodeConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.code_conflict"));
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.resource_not_found"));
    }

    private static void assertParentDisabled(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.parent_disabled"));
    }

    /** 集成测试为审计列提供稳定 Actor；生产请求仍由认证上下文提供真实 Actor。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestActorConfiguration {
        /** @return 仅用于本隔离测试上下文的稳定审计 Actor */
        @Bean
        CurrentActorProvider testCurrentActorProvider() {
            return () -> Optional.of(new AuditActor("mdm-it-actor", ActorType.USER));
        }
    }
}
