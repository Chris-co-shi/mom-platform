package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.i18n.runtime.I18nRuntimeService;
import io.github.chrisshi.mom.system.application.SystemV1Exception;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryApplication;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ChangeStatus;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateItem;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在独立 MySQL 数据库上验证 System 当前基线、运行时译文和字典用例。 */
@Testcontainers
@SpringBootTest(classes = MomSystemApplication.class, properties = {
        // 加载实际部署配置，避免测试资源中的 PG 专用迁移目标覆盖 MySQL。
        "spring.config.location=file:src/main/resources/application.yml",
        "MOM_DATABASE_VENDOR=mysql",
        "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=false"
})
@Import(SystemV1PostgresqlIT.TestActorConfiguration.class)
class SystemMysqlIT {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("mom_system")
            .withCommand("--log-bin-trust-function-creators=ON");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_URL", () -> "jdbc:postgresql://127.0.0.1:1/unexpected_postgresql");
        registry.add("MYSQL_URL", MYSQL::getJdbcUrl);
        registry.add("MYSQL_USERNAME", MYSQL::getUsername);
        registry.add("MYSQL_PASSWORD", MYSQL::getPassword);
    }

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private DictionaryApplication dictionary;
    @Autowired private I18nRuntimeService runtime;

    @Test
    void shouldUseMysqlBaselineAndReadSeedTranslations() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("mom_system");
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2");
        assertThat(runtime.load("zh-CN").messages()).containsEntry("navigation.mdm", "主数据中心");
        assertThat(runtime.load("en-US").messages()).containsEntry("navigation.mdmroutings", "Routings");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE system_supported_locale SET is_default = TRUE WHERE locale_code = 'en-US'"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbc.queryForObject(
                "SELECT locale_code FROM system_supported_locale WHERE is_default", String.class)).isEqualTo("zh-CN");
    }

    @Test
    void shouldKeepDisabledDictionaryHistoryAndRejectDuplicateCode() {
        var type = dictionary.createType(new CreateType("system.test.mysql", "测试状态", true, null));
        var item = dictionary.createItem(type.id(), new CreateItem("ready", "就绪", 10, true, null));
        assertThatThrownBy(() -> dictionary.createItem(
                type.id(), new CreateItem("ready", "重复", 20, true, null)))
                .isInstanceOf(SystemV1Exception.Conflict.class);

        dictionary.changeItemStatus(type.id(), item.id(), new ChangeStatus(false, item.version()));
        assertThat(dictionary.activeItems(type.code())).isEmpty();
        var historical = dictionary.resolveItem(type.code(), "ready");
        assertThat(historical.value()).isEqualTo("就绪");
        assertThat(historical.selectable()).isFalse();
    }
}
