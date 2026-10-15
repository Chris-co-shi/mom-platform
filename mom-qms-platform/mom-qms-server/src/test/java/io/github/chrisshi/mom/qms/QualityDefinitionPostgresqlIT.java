package io.github.chrisshi.mom.qms;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.api.*;
import io.github.chrisshi.mom.mdm.client.MdmReferenceClient;
import io.github.chrisshi.mom.qms.application.QmsException;
import io.github.chrisshi.mom.qms.application.QualityDefinitionApplication;
import io.github.chrisshi.mom.qms.application.model.QualityModels.ItemInput;
import io.github.chrisshi.mom.webmvc.response.Result;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** QMS 质量定义的真实 PostgreSQL、Flyway、事务与失败路径验收。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomQmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
                        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration," +
                        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
                        "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration," +
                        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"})
@Import(QualityDefinitionPostgresqlIT.TestConfigurationBeans.class)
class QualityDefinitionPostgresqlIT {
    private static final String SCHEMA = "mom_qms";
    private static final String PLANT = "1001";
    private static final String MATERIAL = "2001";
    private static final String UOM = "3001";
    @Container static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine")).withDatabaseName("mom_platform")
            .withUsername("mom").withPassword("mom").withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");
    @Autowired QualityDefinitionApplication application;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MdmReferenceClient mdmClient;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicBoolean calledInsideTransaction = new AtomicBoolean(false);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl() + "&currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }
    @BeforeEach void clean() {
        jdbc.update("TRUNCATE TABLE qms_specification_item, qms_specification_revision, qms_quality_specification, qms_inspection_method, qms_inspection_characteristic");
        available.set(true); calledInsideTransaction.set(false);
        when(mdmClient.getMaterialReference(anyString())).thenAnswer(invocation -> {
            observe(); String id = invocation.getArgument(0);
            return Result.success(new MaterialReference(id, "DMC", "碳酸二甲酯", "20", UOM,
                    "RAW_MATERIAL", "REQUIRED", 365, 3, 7L, available.get(), available.get() ? null : "MATERIAL_DISABLED"));
        });
        when(mdmClient.getManufacturingResourceReference(anyString(), anyString())).thenAnswer(invocation -> {
            observe(); String type = invocation.getArgument(0); String id = invocation.getArgument(1);
            return Result.success(new ManufacturingResourceReference(type, id, "PLANT", "工厂", null,
                    id, "Asia/Shanghai", null, 4L, available.get(), available.get() ? null : "PLANT_DISABLED"));
        });
        when(mdmClient.getUomReference(anyString())).thenAnswer(invocation -> {
            observe(); String id = invocation.getArgument(0);
            return Result.success(new UomReference(id, "PCT", "%", "%", "31", "32", true,
                    null, 2L, available.get(), available.get() ? null : "UOM_DISABLED"));
        });
    }

    @Test void migrationMustCreateOnlyQmsOwnedTablesAndNoForeignKeys() {
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=true ORDER BY installed_rank DESC LIMIT 1", String.class)).isEqualTo("3");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name LIKE 'qms_%'", Long.class, SCHEMA)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema=? AND constraint_type='FOREIGN KEY'", Long.class, SCHEMA)).isZero();
        assertThat(jdbc.queryForObject("SHOW timezone", String.class)).isEqualTo("UTC");
    }

    @Test void dmcIncomingSpecificationMustReleaseImmutableRevisionAndPreserveSnapshots() {
        var characteristic = application.createCharacteristic("PURITY", "纯度", UOM);
        var method = application.createMethod("GC", "气相色谱", "DMC 来料纯度检测");
        var detail = application.createSpecification("DMC-IN", "DMC 来料标准", PLANT, MATERIAL,
                List.of(item(characteristic.id(), method.id(), "99.90", "99.95", "100.00")));
        assertThat(detail.status()).isEqualTo("DRAFT");
        assertThat(detail.materialRevisionNo()).isEqualTo(3);
        var released = application.release(detail.revisionId(), detail.lockVersion());
        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(released.items().getFirst().lowerLimit()).isEqualTo("99.900000000");
        assertThatThrownBy(() -> application.updateDraft(released.revisionId(), released.lockVersion(),
                List.of(item(characteristic.id(), method.id(), "99", "99.5", "100"))))
                .isInstanceOf(QmsException.class).extracting("code").isEqualTo("qms.immutable_definition");
        var obsolete = application.obsolete(released.revisionId(), released.lockVersion());
        assertThat(obsolete.status()).isEqualTo("OBSOLETE");
        var next = application.nextRevision(detail.id());
        assertThat(next.revisionNo()).isEqualTo(2);
        assertThat(next.status()).isEqualTo("DRAFT");
    }

    @Test void invalidRangeOrDisabledMdmReferenceMustFailWithoutLocalWrite() {
        var characteristic = application.createCharacteristic("WATER", "水分", UOM);
        var method = application.createMethod("KF", "卡尔费休", null);
        assertThatThrownBy(() -> application.createSpecification("BAD", "非法范围", PLANT, MATERIAL,
                List.of(item(characteristic.id(), method.id(), "10", "5", "1"))))
                .isInstanceOf(QmsException.class).extracting("code").isEqualTo("qms.invalid_range");
        available.set(false);
        assertThatThrownBy(() -> application.createSpecification("BLOCKED", "停用物料", PLANT, MATERIAL,
                List.of(item(characteristic.id(), method.id(), "1", "2", "3"))))
                .isInstanceOf(QmsException.class).extracting("code").isEqualTo("qms.disabled_reference");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qms_quality_specification", Long.class)).isZero();
    }

    @Test void allMdmCallsMustRunOutsideQmsTransaction() {
        var characteristic = application.createCharacteristic("DENSITY", "密度", UOM);
        var method = application.createMethod("DENSITY-M", "密度法", null);
        application.createSpecification("TX", "事务边界", PLANT, MATERIAL,
                List.of(item(characteristic.id(), method.id(), "1.1", "1.2", "1.3")));
        assertThat(calledInsideTransaction).isFalse();
    }
    private static ItemInput item(String characteristicId, String methodId, String low, String target, String high) {
        return new ItemInput(10, characteristicId, methodId, UOM, low, target, high, true);
    }
    private void observe() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) calledInsideTransaction.set(true);
    }

    @TestConfiguration
    static class TestConfigurationBeans {
        @Bean CurrentActorProvider actor() {
            return () -> Optional.of(new AuditActor("qms-it", ActorType.USER));
        }
    }
}
