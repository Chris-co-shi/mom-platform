package io.github.chrisshi.mom.architecture;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOM 持久化类型位置、技术栈与 Entity 能力门禁。
 *
 * <p>ADR-042 后，Level 1 允许 Application 直接依赖本服务 Mapper/Entity，因此本测试不再把
 * Repository Port/Adapter 当作所有 bounded context 的强制中间层；System V1 也按 Level 1 直接复用
 * Mapper/Entity，不再保留没有替换收益的一对一 Repository Adapter。</p>
 */
class PersistenceArchitectureTest {
    private static final String[] BOUNDED_CONTEXT_PACKAGES = {
            "io.github.chrisshi.mom.auth..", "io.github.chrisshi.mom.iam..",
            "io.github.chrisshi.mom.mdm..", "io.github.chrisshi.mom.integration..",
            "io.github.chrisshi.mom.system..", "io.github.chrisshi.mom.mes..",
            "io.github.chrisshi.mom.wms..", "io.github.chrisshi.mom.qms..",
            "io.github.chrisshi.mom.ems..", "io.github.chrisshi.mom.eam..",
            "io.github.chrisshi.mom.traceability.."
    };

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** 所有 Mapper 必须位于明确的 Infrastructure Mapper/Query 技术职责包。 */
    @Test
    void businessMappersMustStayInInfrastructureMapperOrQueryPackages() {
        classes().that().haveSimpleNameEndingWith("Mapper")
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage(
                        "..infrastructure.mapper",
                        "..infrastructure.query",
                        "..infrastructure.persistence.mapper",
                        "..infrastructure.persistence.query")
                .check(productionClasses);
    }

    /** 普通 MomBaseMapper 实现必须位于 Mapper 职责包，不与专用 QueryMapper 混淆。 */
    @Test
    void momBaseMappersMustStayInMapperPackages() {
        classes().that().areAssignableTo(MomBaseMapper.class)
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage(
                        "..infrastructure.mapper",
                        "..infrastructure.persistence.mapper")
                .check(productionClasses);
    }

    /** Entity 可位于简化 Infrastructure 或复杂 Persistence 的 Entity 职责包。 */
    @Test
    void databaseEntitiesMustStayInEntityPackages() {
        classes().that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("io.github.chrisshi.mom.data.entity..")
                .should().resideInAnyPackage(
                        "..infrastructure.entity",
                        "..infrastructure.persistence.entity")
                .check(productionClasses);
    }

    /** System V1 普通可维护配置表统一需要审计、乐观锁与逻辑删除能力。 */
    @Test
    void systemEntitiesMustSelectBaseClassByCapability() throws NoSuchFieldException {
        classes()
                .that().resideInAnyPackage("io.github.chrisshi.mom.system.infrastructure..")
                .and().haveSimpleNameEndingWith("Entity")
                .should().beAssignableTo(BaseEntity.class)
                .check(productionClasses);
    }

    /** bounded context 不得使用 MyBatis-Plus 通用 Service 代替业务 Application。 */
    @Test
    void boundedContextsMustNotUseMybatisPlusGenericServices() {
        noClasses().that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.spring.service..",
                        "com.baomidou.mybatisplus.extension.service..")
                .check(productionClasses);
    }

    /** 已引入的 MyBatis-Plus Repository 只能留在 Infrastructure Repository 职责包。 */
    @Test
    void mybatisPlusRepositoriesMustStayInsideInfrastructureRepository() {
        noClasses().that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .and().resideOutsideOfPackage("..infrastructure.repository..")
                .and().resideOutsideOfPackage("..infrastructure.persistence.repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.spring.repository..",
                        "com.baomidou.mybatisplus.extension.repository..")
                .check(productionClasses);
    }

    /** Domain Repository Port 一旦存在，就必须保持 MyBatis-Plus 无关。 */
    @Test
    void domainRepositoryPortsMustRemainFrameworkIndependent() {
        noClasses().that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat().resideInAnyPackage("com.baomidou.mybatisplus..")
                .check(productionClasses);
    }

    /** Controller/Web/Domain 不得直接依赖具体 MyBatis Repository；Level 1 Application 允许按需使用。 */
    @Test
    void inboundAndDomainLayersMustNotDependOnConcreteMybatisRepositories() {
        noClasses().that().resideInAnyPackage(
                        "..domain..", "..controller..", "..web..", "..interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.repository..",
                        "..infrastructure.persistence.repository..")
                .check(productionClasses);
    }

    /** 正式 bounded context 不得无 ADR 引入直接 JDBC 访问。 */
    @Test
    void boundedContextsMustNotIntroduceDirectJdbcAccess() {
        noClasses().that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.security.IamAuthorizationServerProtocolConfiguration")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "java.sql..")
                .check(productionClasses);
    }

    /** 所有持久化 Entity 都不得使用 LocalDateTime 表示全球时间点。 */
    @Test
    void persistenceEntitiesMustNotUseLocalDateTime() {
        noClasses().that().resideInAnyPackage(
                        "..infrastructure.entity..",
                        "..infrastructure.persistence.entity..")
                .and().haveSimpleNameEndingWith("Entity")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.time.LocalDateTime")
                .check(productionClasses);
    }

    /** Framework 共享 Entity 能力字段类型必须保持稳定。 */
    @Test
    void sharedEntityCapabilitiesMustKeepStableTypes() throws NoSuchFieldException {
        Field id = BaseIdEntity.class.getDeclaredField("id");
        Field version = BaseEntity.class.getDeclaredField("version");
        Field deleted = BaseEntity.class.getDeclaredField("deleted");
        assertThat(id.getType()).isEqualTo(String.class);
        assertThat(version.getType()).isEqualTo(Long.class);
        assertThat(version.getAnnotation(Version.class)).isNotNull();
        assertThat(deleted.getType()).isEqualTo(Boolean.class);
        assertThat(deleted.getAnnotation(TableLogic.class)).isNotNull();
    }
}
