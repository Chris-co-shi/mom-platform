package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.i18n.runtime.I18nRuntimeService;
import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataRules;
import io.github.chrisshi.mom.mdm.application.UomConversionApplication;
import io.github.chrisshi.mom.mdm.application.UomMasterDataApplication;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.UomConversionRulePageParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在真实 MySQL 上验证 MDM 基线种子和计量单位规则版本读写。 */
@Testcontainers
@SpringBootTest(classes = MomMdmApplication.class, properties = {
        "MOM_DATABASE_VENDOR=mysql",
        "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=false"
})
@Import(MdmMasterDataPostgresqlIT.TestActorConfiguration.class)
class MdmMysqlIT {
    private static final String LENGTH_CATEGORY_ID = "800000000000000103";
    private static final String METRE_ID = "800000000000000206";
    private static final String SECOND_ID = "800000000000000203";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("mom_mdm")
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
    @Autowired private I18nRuntimeService runtime;
    @Autowired private UomMasterDataApplication units;
    @Autowired private UomConversionApplication conversion;

    @Test
    void shouldReadBaselineLocaleAndDecimalConversionSeeds() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("mom_mdm");
        assertThat(runtime.load("en-US").messages())
                .containsEntry("mdm.error.code_conflict", "The {0} code already exists");
        assertThat(conversion.convertCurrent("25", "800000000000000219", "800000000000000218").targetValue())
                .isEqualTo("298.15");
    }

    @Test
    void shouldPersistExactDecimalAndReplayPreviousRuleVersion() {
        var unit = units.createUom("mom:mysql-probe", "测试单位", "probe", LENGTH_CATEGORY_ID,
                "集成测试单位", MdmMasterDataRules.ENABLED, new BigDecimal("0.123456789012345678"),
                BigDecimal.ZERO, 34, "HALF_EVEN");
        assertThat(conversion.convertCurrent("2", unit.id(), METRE_ID).targetValue())
                .isEqualTo("0.246913578024691356");
        var next = units.publishRule(unit.id(), 1, new BigDecimal("0.2"), BigDecimal.ZERO, 34, "HALF_EVEN");
        assertThat(next.versionNo()).isEqualTo(2);
        assertThat(conversion.convertCurrent("2", unit.id(), METRE_ID).targetValue()).isEqualTo("0.4");
        assertThat(conversion.replay("2", unit.id(), METRE_ID, 1, null).targetValue())
                .isEqualTo("0.246913578024691356");
        assertThat(units.pageRules(new PageQuery<>(new UomConversionRulePageParams(unit.id()), 1, 1)).records())
                .singleElement().satisfies(rule -> assertThat(rule.versionNo()).isEqualTo(2));

        assertThatThrownBy(() -> conversion.convertCurrent("1", unit.id(), SECOND_ID))
                .isInstanceOfSatisfying(MdmException.class,
                        error -> assertThat(error.code()).isEqualTo("mdm.incompatible_uom"));
    }
}
