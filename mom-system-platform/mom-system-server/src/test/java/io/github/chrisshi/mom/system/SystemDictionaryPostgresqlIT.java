package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateDictionaryCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateItemCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageQuery;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageQuery;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.StatusCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateDictionaryCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateItemCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationService;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System Dictionary 的真实 PostgreSQL 17.7、Flyway V1→V7、MyBatis-Plus、审计与乐观锁集成测试。
 *
 * <p>测试使用动态端口和独立 mom_system Schema，每例清理字典数据，不依赖本机数据库。它验证同 Schema
 * 无物理外键完整性、数据库 Check/Unique、BaseEntity 逻辑删除列、Active/Compatibility、分页和 Parameter V1
 * 兼容；Docker 不可用时 Testcontainers 明确跳过，不能描述为专项成功。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomSystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=false"
        })
@Import(SystemParameterPostgresqlIT.TestActorConfiguration.class)
class SystemDictionaryPostgresqlIT {
    private static final String SCHEMA = "mom_system";
    private static final String UPGRADE_SCHEMA = "mom_system_upgrade";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Flyway flyway;
    @Autowired
    private SystemDictionaryApplicationService service;

    /** 注入动态 PostgreSQL 地址和受控 mom_system search_path。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=mom-system-server");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("TRUNCATE TABLE system_dictionary_item, system_dictionary, system_parameter");
    }

    @AfterEach
    void cleanUpgradeSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + UPGRADE_SCHEMA + " CASCADE");
    }

    @Test
    void freshDatabaseMustApplyV1ThroughV7AndPreserveParameterTable() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success and version in ('1','2','3','4','5','6')",
                Long.class)).isEqualTo(6L);
        assertThat(jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                 where table_schema=? and table_name in (
                   'system_parameter','system_dictionary','system_dictionary_item')
                 order by table_name
                """, String.class, SCHEMA))
                .containsExactly("system_dictionary", "system_dictionary_item", "system_parameter");
        assertThat(jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_schema=? and table_name='system_dictionary' and column_name='id'",
                Integer.class, SCHEMA)).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema=?
                   and table_name in ('system_parameter','system_dictionary','system_dictionary_item')
                   and column_name='deleted' and data_type='boolean' and is_nullable='NO'
                """, Long.class, SCHEMA)).isEqualTo(3L);
    }

    @Test
    void existingV1SchemaMustUpgradeThroughV8WithoutChangingParameterData() {
        Flyway v1 = Flyway.configure().dataSource(dataSource).createSchemas(true)
                .schemas(UPGRADE_SCHEMA).defaultSchema(UPGRADE_SCHEMA)
                .locations("classpath:db/migration/system").target("1").load();
        v1.migrate();
        jdbcTemplate.update("""
                insert into mom_system_upgrade.system_parameter (
                    id, scope_type, scope_code, parameter_key, value_type, parameter_value,
                    enabled, version, created_by, created_at, updated_by, updated_at)
                values ('upgrade-param', 'GLOBAL', '', 'upgrade.key', 'STRING', 'kept',
                    true, 0, 'test', now(), 'test', now())
                """);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema=? and table_name='system_dictionary'",
                Long.class, UPGRADE_SCHEMA)).isZero();

        Flyway latest = Flyway.configure().dataSource(dataSource).createSchemas(true)
                .schemas(UPGRADE_SCHEMA).defaultSchema(UPGRADE_SCHEMA)
                .locations("classpath:db/migration/system").load();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema=? and table_name='system_dictionary'",
                Long.class, UPGRADE_SCHEMA)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select parameter_value from mom_system_upgrade.system_parameter where id='upgrade-param'",
                String.class)).isEqualTo("kept");
        assertThat(jdbcTemplate.queryForObject(
                "select deleted from mom_system_upgrade.system_parameter where id='upgrade-param'",
                Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema=? and table_name in (
                   'system_application','system_navigation_item','system_catalog_release')
                """, Long.class, UPGRADE_SCHEMA)).isEqualTo(3L);
    }

    @Test
    void databaseMustEnforceCodeUniqueAndSortWhileApplicationRejectsMissingParent() {
        insertDictionary("dictionary-one", "system.common.state");
        assertThatThrownBy(() -> insertDictionary("dictionary-two", "system.common.state"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDictionary("dictionary-bad", "single"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> service.createItem("missing", item("ready", "Ready", 0, true)))
                .isInstanceOf(SystemDictionaryException.NotFound.class);

        insertItem("item-one", "dictionary-one", "ready", 10);
        assertThatThrownBy(() -> insertItem("item-two", "dictionary-one", "ready", 20))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem("item-bad", "dictionary-one", "1ready", 20))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem("item-sort", "dictionary-one", "later", 1_000_001))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mybatisPlusMustFillAuditLockVersionsAndSupportActiveCompatibilityAndPages() {
        var dictionary = service.createDictionary(
                new CreateDictionaryCommand("System.Common.State", "State", null, true));
        var zeta = service.createItem(dictionary.id(), item("zeta", "Zeta", 10, true));
        service.createItem(dictionary.id(), item("alpha", "Alpha", 10, true));
        var disabled = service.createItem(dictionary.id(), item("disabled", "Disabled", 0, false));

        assertThat(dictionary.id()).matches("[0-9]{1,19}");
        assertThat(dictionary.createdBy()).isEqualTo("s13-test-actor");
        assertThat(zeta.id()).matches("[0-9]{1,19}");
        assertThat(service.activeItems("system.common.state"))
                .extracting(option -> option.itemCode()).containsExactly("alpha", "zeta");
        assertThat(service.resolveItem("system.common.state", disabled.itemCode()).effectiveEnabled()).isFalse();

        var updatedDictionary = service.updateDictionary(dictionary.id(),
                new UpdateDictionaryCommand("Display State", null, dictionary.version()));
        assertThat(updatedDictionary.version()).isEqualTo(1L);
        assertThatThrownBy(() -> service.updateDictionary(dictionary.id(),
                new UpdateDictionaryCommand("Stale", null, dictionary.version())))
                .isInstanceOf(SystemDictionaryException.StaleVersion.class);

        var updatedItem = service.updateItem(dictionary.id(), zeta.id(),
                new UpdateItemCommand("Zeta Label", 1, null, zeta.version()));
        assertThat(updatedItem.version()).isEqualTo(1L);
        assertThatThrownBy(() -> service.updateItem(dictionary.id(), zeta.id(),
                new UpdateItemCommand("Stale", 2, null, zeta.version())))
                .isInstanceOf(SystemDictionaryException.StaleVersion.class);

        assertThat(service.pageDictionaries(new DictionaryPageQuery(null, true, 0, 1)).total()).isEqualTo(1L);
        assertThat(service.pageItems(dictionary.id(), new ItemPageQuery(null, null, 0, 2)).items())
                .extracting(view -> view.itemCode()).containsExactly("disabled", "zeta");

        service.changeDictionaryStatus(dictionary.id(), new StatusCommand(false, updatedDictionary.version()));
        assertThat(service.activeItems(dictionary.dictionaryCode())).isEmpty();
        assertThat(service.resolveItem(dictionary.dictionaryCode(), zeta.itemCode()).itemEnabled()).isTrue();
        assertThat(service.resolveItem(dictionary.dictionaryCode(), zeta.itemCode()).effectiveEnabled()).isFalse();
    }

    @Test
    void businessForeignKeysMustBeAbsentAndAssociationIndexMustRemain() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_schema=? AND constraint_type='FOREIGN KEY'
                   AND table_name IN ('system_dictionary_item','system_i18n_message','system_i18n_release')
                """, Long.class, SCHEMA)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes
                 WHERE schemaname=? AND tablename='system_dictionary_item'
                   AND indexname='ix_system_dictionary_item_active'
                """, Long.class, SCHEMA)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM system_dictionary_item item
                  LEFT JOIN system_dictionary dictionary ON dictionary.id=item.dictionary_id
                 WHERE dictionary.id IS NULL
                """, Long.class)).isZero();
    }

    private void insertDictionary(String id, String code) {
        jdbcTemplate.update("""
                INSERT INTO system_dictionary (
                    id, dictionary_code, dictionary_name, enabled, version,
                    created_by, created_at, updated_by, updated_at)
                VALUES (?, ?, 'State', true, 0, 'test', now(), 'test', now())
                """, id, code);
    }

    private void insertItem(String id, String dictionaryId, String code, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO system_dictionary_item (
                    id, dictionary_id, item_code, item_label, sort_order, enabled, version,
                    created_by, created_at, updated_by, updated_at)
                VALUES (?, ?, ?, 'Ready', ?, true, 0, 'test', now(), 'test', now())
                """, id, dictionaryId, code, sortOrder);
    }

    private static CreateItemCommand item(String code, String label, int sortOrder, boolean enabled) {
        return new CreateItemCommand(code, label, sortOrder, null, enabled);
    }
}
