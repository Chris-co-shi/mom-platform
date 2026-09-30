package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.application.FactoryStructureApplication;
import io.github.chrisshi.mom.mdm.application.LocationMasterDataApplication;
import io.github.chrisshi.mom.mdm.application.MaterialCategoryApplication;
import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataRules;
import io.github.chrisshi.mom.mdm.application.WarehouseStructureApplication;
import io.github.chrisshi.mom.mdm.application.UomConversionApplication;
import io.github.chrisshi.mom.mdm.application.UomMasterDataApplication;
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

import java.util.Optional;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MDM 工厂、仓储、位置与 MaterialCategory 主数据的真实 PostgreSQL 集成验收。
 *
 * <p>该测试执行真实 Flyway、MyBatis-Plus、审计填充、分页、唯一约束、父级校验、分类循环检测和
 * 乐观锁。测试方法顺序不构成依赖，每次均清空本 Slice 表；它不启动 Nacos、Redis、MQ 或 Seata，
 * Docker 不可用时由 Testcontainers 显式跳过，不能将跳过描述为通过。</p>
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
    @Autowired private MaterialCategoryApplication materialCategoryApplication;
    @Autowired private UomMasterDataApplication uomMasterDataApplication;
    @Autowired private UomConversionApplication uomConversionApplication;
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

    /** 每个测试清理 MDM 普通主数据；没有物理外键，因此不依赖级联顺序。 */
    @BeforeEach
    void cleanTables() {
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

    /** 验证截至 V105 的表、命名约束、查询索引和无物理外键策略。 */
    @Test
    void migrationMustCreateNineMasterTablesAndDatabaseConstraints() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT version FROM flyway_schema_history
                 WHERE success = true ORDER BY installed_rank DESC LIMIT 1
                """, String.class)).isEqualTo("105");
        assertThat(jdbcTemplate.queryForObject("select current_schema()", String.class)).isEqualTo(SCHEMA);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                 WHERE table_schema = ? AND table_name IN (
                   'mdm_plant', 'mdm_workshop', 'mdm_production_line', 'mdm_workstation',
                   'mdm_warehouse', 'mdm_warehouse_area', 'mdm_location_type', 'mdm_location',
                   'mdm_material_category', 'mdm_dimension', 'mdm_uom_category', 'mdm_uom',
                   'mdm_uom_conversion_rule')
                """, Long.class, SCHEMA)).isEqualTo(13L);
        assertThat(jdbcTemplate.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                 WHERE table_schema = ? AND constraint_type = 'UNIQUE'
                   AND constraint_name LIKE 'uk_mdm_%'
                 ORDER BY constraint_name
                """, String.class, SCHEMA)).containsExactlyInAnyOrder(
                "uk_mdm_plant_code", "uk_mdm_workshop_plant_code",
                "uk_mdm_production_line_workshop_code", "uk_mdm_workstation_line_code",
                "uk_mdm_warehouse_plant_code", "uk_mdm_warehouse_area_warehouse_code",
                "uk_mdm_location_type_code", "uk_mdm_location_plant_code",
                "uk_mdm_material_category_code", "uk_mdm_dimension_code", "uk_mdm_dimension_vector",
                "uk_mdm_uom_category_code", "uk_mdm_uom_code", "uk_mdm_uom_conversion_rule_version");
        assertThat(jdbcTemplate.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                 WHERE table_schema = ? AND table_name = 'mdm_material_category'
                   AND constraint_type = 'CHECK'
                """, String.class, SCHEMA)).contains(
                "ck_mdm_material_category_status",
                "ck_mdm_material_category_shelf_life_non_negative",
                "ck_mdm_material_category_version_non_negative");
        assertThat(jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = ? AND tablename = 'mdm_material_category'
                """, String.class, SCHEMA)).contains("ix_mdm_material_category_parent_sort");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_schema = ? AND constraint_type = 'FOREIGN KEY'
                """, Long.class, SCHEMA)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_dimension WHERE created_by = 'flyway:uom-seed'", Long.class)).isEqualTo(7L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_uom WHERE created_by = 'flyway:uom-seed'", Long.class)).isEqualTo(20L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM mdm_uom_conversion_rule WHERE created_by = 'flyway:uom-seed'", Long.class))
                .isEqualTo(13L);
    }

    /** 覆盖根/子分类创建、默认建议值、全局 Code 唯一、父级存在/状态和非负保质期。 */
    @Test
    void materialCategoryCreationMustEnforceDefaultsAndParentRules() {
        var root = materialCategoryApplication.createMaterialCategory(
                "RAW", "原材料", "Raw Material", null, 10, true, 365, MdmMasterDataRules.ENABLED);
        var child = materialCategoryApplication.createMaterialCategory(
                "ADDITIVE", "添加剂", null, root.id(), 20, null, 0, MdmMasterDataRules.ENABLED);

        assertThat(root.parentId()).isNull();
        assertThat(root.defaultBatchManaged()).isTrue();
        assertThat(root.defaultShelfLifeDays()).isEqualTo(365);
        assertThat(child.parentId()).isEqualTo(root.id());
        assertThat(child.defaultBatchManaged()).isNull();
        assertCodeConflict(() -> materialCategoryApplication.createMaterialCategory(
                "RAW", "重复分类", null, null, 30, false, null, MdmMasterDataRules.ENABLED));
        assertNotFound(() -> materialCategoryApplication.createMaterialCategory(
                "ORPHAN", "孤儿分类", null, "missing", 30, null, null, MdmMasterDataRules.ENABLED));

        var disabledParent = materialCategoryApplication.createMaterialCategory(
                "DISABLED-PARENT", "停用父分类", null, null, 40, null, null, MdmMasterDataRules.DISABLED);
        assertParentDisabled(() -> materialCategoryApplication.createMaterialCategory(
                "BLOCKED-CHILD", "禁止创建", null, disabledParent.id(), 1, null, null,
                MdmMasterDataRules.ENABLED));
        assertValidationFailed(() -> materialCategoryApplication.createMaterialCategory(
                "NEGATIVE-SHELF", "非法保质期", null, null, 50, null, -1, MdmMasterDataRules.ENABLED));
    }

    /** 覆盖允许移动、Code 不变、自引用拒绝、后代循环拒绝、脏循环防护和乐观锁冲突。 */
    @Test
    void materialCategoryUpdateMustMoveWithoutCreatingTreeCycles() {
        var rootA = materialCategoryApplication.createMaterialCategory(
                "A", "分类 A", null, null, 1, null, null, MdmMasterDataRules.ENABLED);
        var childB = materialCategoryApplication.createMaterialCategory(
                "B", "分类 B", null, rootA.id(), 2, false, 30, MdmMasterDataRules.ENABLED);
        var childC = materialCategoryApplication.createMaterialCategory(
                "C", "分类 C", null, childB.id(), 3, null, null, MdmMasterDataRules.ENABLED);
        var rootD = materialCategoryApplication.createMaterialCategory(
                "D", "分类 D", null, null, 4, null, null, MdmMasterDataRules.ENABLED);

        assertInvalidReference(() -> materialCategoryApplication.updateMaterialCategory(
                rootA.id(), rootA.nameZh(), rootA.nameEn(), childC.id(), rootA.sort(),
                rootA.defaultBatchManaged(), rootA.defaultShelfLifeDays(), rootA.version()));
        var moved = materialCategoryApplication.updateMaterialCategory(
                childB.id(), "分类 B 已移动", "Moved B", rootD.id(), 5, true, 60, childB.version());
        assertThat(moved.code()).isEqualTo("B");
        assertThat(moved.parentId()).isEqualTo(rootD.id());
        assertThat(moved.nameZh()).isEqualTo("分类 B 已移动");
        assertThat(moved.defaultBatchManaged()).isTrue();
        assertThat(moved.version()).isEqualTo(1L);
        var movedToRoot = materialCategoryApplication.updateMaterialCategory(
                moved.id(), "分类 B 根节点", null, null, 6, null, null, moved.version());
        var reloadedRoot = materialCategoryApplication.getMaterialCategory(movedToRoot.id());
        assertThat(reloadedRoot.code()).isEqualTo("B");
        assertThat(reloadedRoot.parentId()).isNull();
        assertThat(reloadedRoot.nameEn()).isNull();
        assertThat(reloadedRoot.defaultBatchManaged()).isNull();
        assertThat(reloadedRoot.defaultShelfLifeDays()).isNull();
        assertInvalidReference(() -> materialCategoryApplication.updateMaterialCategory(
                movedToRoot.id(), movedToRoot.nameZh(), movedToRoot.nameEn(), movedToRoot.id(), movedToRoot.sort(),
                movedToRoot.defaultBatchManaged(), movedToRoot.defaultShelfLifeDays(), movedToRoot.version()));
        assertVersionConflict(() -> materialCategoryApplication.updateMaterialCategory(
                childB.id(), "陈旧更新", null, rootD.id(), 6, null, null, childB.version()));

        var dirtyX = materialCategoryApplication.createMaterialCategory(
                "DIRTY-X", "脏节点 X", null, null, 10, null, null, MdmMasterDataRules.ENABLED);
        var dirtyY = materialCategoryApplication.createMaterialCategory(
                "DIRTY-Y", "脏节点 Y", null, null, 11, null, null, MdmMasterDataRules.ENABLED);
        var movable = materialCategoryApplication.createMaterialCategory(
                "MOVABLE", "待移动节点", null, null, 12, null, null, MdmMasterDataRules.ENABLED);
        jdbcTemplate.update("UPDATE mdm_material_category SET parent_id = ? WHERE id = ?", dirtyY.id(), dirtyX.id());
        jdbcTemplate.update("UPDATE mdm_material_category SET parent_id = ? WHERE id = ?", dirtyX.id(), dirtyY.id());
        assertInvalidReference(() -> materialCategoryApplication.updateMaterialCategory(
                movable.id(), movable.nameZh(), movable.nameEn(), dirtyX.id(), movable.sort(),
                movable.defaultBatchManaged(), movable.defaultShelfLifeDays(), movable.version()));
    }

    /** 覆盖显式启停、父级停用禁止启用子级，以及 parentId/status 组合分页和 PageResult 元数据。 */
    @Test
    void materialCategoryLifecycleAndPageMustRespectParentAndFilters() {
        var parentA = materialCategoryApplication.createMaterialCategory(
                "PARENT-A", "父分类 A", null, null, 1, null, null, MdmMasterDataRules.ENABLED);
        var parentB = materialCategoryApplication.createMaterialCategory(
                "PARENT-B", "父分类 B", null, null, 2, null, null, MdmMasterDataRules.ENABLED);
        var enabledChild = materialCategoryApplication.createMaterialCategory(
                "ENABLED-CHILD", "启用子分类", null, parentA.id(), 20, null, null, MdmMasterDataRules.ENABLED);
        var disabledChild = materialCategoryApplication.createMaterialCategory(
                "DISABLED-CHILD", "停用子分类", null, parentA.id(), 10, null, null,
                MdmMasterDataRules.DISABLED);
        materialCategoryApplication.createMaterialCategory(
                "OTHER-CHILD", "其他父级子分类", null, parentB.id(), 5, null, null,
                MdmMasterDataRules.ENABLED);

        var enabledPage = materialCategoryApplication.pageMaterialCategories(
                parentA.id(), MdmMasterDataRules.ENABLED, 1, 20);
        assertThat(enabledPage.records()).extracting(record -> record.id()).containsExactly(enabledChild.id());
        assertThat(enabledPage.pageNo()).isEqualTo(1);
        assertThat(enabledPage.pageSize()).isEqualTo(20);
        assertThat(enabledPage.total()).isEqualTo(1);
        assertThat(enabledPage.totalPages()).isEqualTo(1);
        var disabledPage = materialCategoryApplication.pageMaterialCategories(
                parentA.id(), MdmMasterDataRules.DISABLED, 1, 20);
        assertThat(disabledPage.records()).extracting(record -> record.id()).containsExactly(disabledChild.id());

        var disabledParent = materialCategoryApplication.disableMaterialCategory(parentA.id(), parentA.version());
        assertParentDisabled(() -> materialCategoryApplication.enableMaterialCategory(
                disabledChild.id(), disabledChild.version()));
        var enabledParent = materialCategoryApplication.enableMaterialCategory(
                disabledParent.id(), disabledParent.version());
        var enabledPreviouslyDisabledChild = materialCategoryApplication.enableMaterialCategory(
                disabledChild.id(), disabledChild.version());
        assertThat(enabledParent.status()).isEqualTo(MdmMasterDataRules.ENABLED);
        assertThat(enabledPreviouslyDisabledChild.status()).isEqualTo(MdmMasterDataRules.ENABLED);
        var disabledAgain = materialCategoryApplication.disableMaterialCategory(
                enabledPreviouslyDisabledChild.id(), enabledPreviouslyDisabledChild.version());
        assertThat(disabledAgain.status()).isEqualTo(MdmMasterDataRules.DISABLED);
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

    /** 覆盖类别与基准单位原子创建、父级状态、企业扩展 Code 说明和 PageResult 复用。 */
    @Test
    void uomCatalogMustEnforceReferenceAndParentRules() {
        var dimension = uomMasterDataApplication.createDimension(
                "TEST_LENGTH", "测试长度", "Test length", 9, 1, 0, 0, 0, 0, 0, MdmMasterDataRules.ENABLED);
        assertDimensionConflict(() -> uomMasterDataApplication.createDimension(
                "TEST_LENGTH", "重复编码", null, 10, 2, 0, 0, 0, 0, 0, MdmMasterDataRules.ENABLED));
        assertDimensionConflict(() -> uomMasterDataApplication.createDimension(
                "TEST_LENGTH_VECTOR", "重复向量", null, 9, 1, 0, 0, 0, 0, 0,
                MdmMasterDataRules.ENABLED));
        var category = uomMasterDataApplication.createCategory(
                "TEST_DISTANCE", "测试距离", null, dimension.id(), MdmMasterDataRules.ENABLED,
                "mom:test-m", "测试米", null, "tm", "测试专用非 UCUM 单位");
        var reference = uomMasterDataApplication.getUom(category.referenceUomId());
        assertThat(reference.referenceUnit()).isTrue();
        assertThat(reference.status()).isEqualTo(MdmMasterDataRules.ENABLED);
        var secondCategory = uomMasterDataApplication.createCategory(
                "TEST_DISTANCE_2", "测试距离二", null, dimension.id(), MdmMasterDataRules.ENABLED,
                "mom:test-m-2", "测试米二", null, "tm2", "测试专用非 UCUM 单位");
        var categoryPage = uomMasterDataApplication.pageCategories(dimension.id(), null, 1, 1_000);
        assertThat(categoryPage.pageSize()).isEqualTo(200);
        assertThat(categoryPage.records()).extracting(record -> record.id())
                .containsExactly(category.id(), secondCategory.id());
        assertThat(categoryPage.records()).extracting(record -> record.referenceUomId())
                .containsExactly(category.referenceUomId(), secondCategory.referenceUomId());
        assertThatThrownBy(() -> uomMasterDataApplication.disableUom(reference.id(), reference.version()))
                .isInstanceOfSatisfying(MdmException.class,
                        exception -> assertThat(exception.code()).isEqualTo("mdm.immutable_master_data"));

        var centimetre = uomMasterDataApplication.createUom(
                "mom:test-cm", "测试厘米", null, "tcm", category.id(), "测试专用非 UCUM 单位",
                MdmMasterDataRules.DISABLED, new BigDecimal("0.01"), BigDecimal.ZERO, 34, "HALF_EVEN");
        var page = uomMasterDataApplication.pageUoms(category.id(), MdmMasterDataRules.DISABLED, false, 1, 20);
        assertThat(page.records()).extracting(record -> record.id()).containsExactly(centimetre.id());
        assertThat(page.total()).isEqualTo(1);

        var disabledCategory = uomMasterDataApplication.disableCategory(category.id(), category.version());
        assertThat(uomMasterDataApplication.getUom(reference.id()).status()).isEqualTo(MdmMasterDataRules.DISABLED);
        assertParentDisabled(() -> uomMasterDataApplication.enableUom(centimetre.id(), centimetre.version()));
        assertParentDisabled(() -> uomMasterDataApplication.createUom(
                "mom:blocked", "禁止单位", null, "x", disabledCategory.id(), "测试扩展",
                MdmMasterDataRules.ENABLED, BigDecimal.ONE, BigDecimal.ZERO, 34, "HALF_EVEN"));

        var enabledCategory = uomMasterDataApplication.enableCategory(disabledCategory.id(), disabledCategory.version());
        assertThat(enabledCategory.status()).isEqualTo(MdmMasterDataRules.ENABLED);
        assertThat(uomMasterDataApplication.getUom(reference.id()).status()).isEqualTo(MdmMasterDataRules.ENABLED);
        assertThat(uomMasterDataApplication.enableUom(centimetre.id(), centimetre.version()).status())
                .isEqualTo(MdmMasterDataRules.ENABLED);
    }

    /** 覆盖星型换算、跨类别拒绝、规则不可变换版和指定历史版本重放。 */
    @Test
    void uomConversionMustUseVersionedRulesAndReplayHistory() {
        assertThat(uomConversionApplication.convertCurrent(
                "25", "800000000000000219", "800000000000000218").targetValue()).isEqualTo("298.15");
        assertThat(new BigDecimal(uomConversionApplication.convertCurrent(
                "32", "800000000000000220", "800000000000000219").targetValue()).abs())
                .isLessThan(new BigDecimal("0.000000000000000000000000001"));

        var length = uomMasterDataApplication.createDimension(
                "D-L", "长度", null, 8, 1, 0, 0, 0, 0, 0, MdmMasterDataRules.ENABLED);
        var lengthCategory = uomMasterDataApplication.createCategory(
                "C-L", "长度类别", null, length.id(), MdmMasterDataRules.ENABLED,
                "mom:metre", "米", null, "m", "集成测试单位");
        var cm = uomMasterDataApplication.createUom(
                "mom:centimetre", "厘米", null, "cm", lengthCategory.id(), "集成测试单位",
                MdmMasterDataRules.ENABLED, new BigDecimal("0.01"), BigDecimal.ZERO, 34, "HALF_EVEN");
        var coarse = uomMasterDataApplication.createUom(
                "mom:coarse", "低精度测试单位", null, "coarse", lengthCategory.id(), "集成测试单位",
                MdmMasterDataRules.ENABLED, new BigDecimal("3"), BigDecimal.ZERO, 2, "HALF_EVEN");

        var identity = uomConversionApplication.convertCurrent("1.2300", coarse.id(), coarse.id());
        assertThat(identity.targetValue()).isEqualTo("1.23");
        assertThat(identity.sourceRuleId()).isNull();
        assertThat(identity.targetRuleId()).isNull();
        assertThat(uomConversionApplication.replay("1.2300", coarse.id(), coarse.id(), null, null).targetValue())
                .isEqualTo("1.23");

        var current = uomConversionApplication.convertCurrent("250", cm.id(), lengthCategory.referenceUomId());
        assertThat(current.targetValue()).isEqualTo("2.5");
        assertThat(current.sourceRuleVersion()).isEqualTo(1);
        var v2 = uomMasterDataApplication.publishRule(
                cm.id(), 1, new BigDecimal("0.02"), BigDecimal.ZERO, 34, "HALF_EVEN");
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(uomConversionApplication.convertCurrent("250", cm.id(), lengthCategory.referenceUomId()).targetValue())
                .isEqualTo("5");
        assertThat(uomConversionApplication.replay("250", cm.id(), lengthCategory.referenceUomId(), 1, null).targetValue())
                .isEqualTo("2.5");
        assertThat(uomMasterDataApplication.pageRules(cm.id(), 1, 20).records())
                .extracting(record -> record.status()).containsExactly(MdmMasterDataRules.ENABLED, MdmMasterDataRules.DISABLED);

        var time = uomMasterDataApplication.createDimension(
                "D-T", "时间", null, 7, 0, 0, 0, 0, 0, 0, MdmMasterDataRules.ENABLED);
        var timeCategory = uomMasterDataApplication.createCategory(
                "C-T", "时间类别", null, time.id(), MdmMasterDataRules.ENABLED,
                "mom:second", "秒", null, "s", "集成测试单位");
        assertThat(uomConversionApplication.compatibility(cm.id(), timeCategory.referenceUomId()).compatible()).isFalse();
        assertThatThrownBy(() -> uomConversionApplication.convertCurrent(
                "1", cm.id(), timeCategory.referenceUomId()))
                .isInstanceOfSatisfying(MdmException.class,
                        exception -> assertThat(exception.code()).isEqualTo("mdm.incompatible_uom"));
    }

    private static void assertCodeConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.code_conflict"));
    }

    private static void assertDimensionConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.dimension_conflict"));
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.resource_not_found"));
    }

    private static void assertParentDisabled(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.parent_disabled"));
    }

    private static void assertInvalidReference(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.invalid_reference"));
    }

    private static void assertValidationFailed(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.validation_failed"));
    }

    private static void assertVersionConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MdmException.class,
                exception -> assertThat(exception.code()).isEqualTo("mdm.version_conflict"));
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
