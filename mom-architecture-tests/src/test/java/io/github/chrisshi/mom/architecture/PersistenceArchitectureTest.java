package io.github.chrisshi.mom.architecture;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nReleaseEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOM 持久化类型位置、技术栈选择与基础 Entity 能力的渐进式架构门禁。
 *
 * <p>本测试分析 Reactor 已编译字节码，不解析 Java import。它自动判断稳定且可证明的事实：业务 Mapper
 * 和 Entity 的包位置、MyBatis-Plus 通用 Service 禁区、正式 bounded context 对 Spring JDBC/java.sql 的
 * 直接依赖、System Entity 按生命周期选择的基类能力以及公共基类字段类型。</p>
 *
 * <p>直接 JDBC 只允许四个已经登记的精确历史/协议例外；不得新增包级排除。System Parameter、Dictionary、
 * Dynamic I18n 及后续正式业务能力必须统一通过 MyBatis-Plus Mapper 体系访问 PostgreSQL。</p>
 */
class PersistenceArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** 业务 Mapper 必须位于 Infrastructure Persistence 并经 MOM 安全基类访问 MyBatis-Plus。 */
    @Test
    void businessMappersMustStayInPersistenceAndUseMomBaseMapper() {
        classes()
                .that().haveSimpleNameEndingWith("Mapper")
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage("..infrastructure.persistence..")
                .andShould().beAssignableTo(MomBaseMapper.class)
                .because("业务 Mapper 属于 Infrastructure Persistence，且 Wrapper-only Update 必须被拒绝")
                .check(productionClasses);
    }

    /** 数据库 Entity 必须位于 Infrastructure Persistence，Framework 能力基类是唯一精确例外。 */
    @Test
    void databaseEntitiesMustStayInPersistencePackages() {
        classes()
                .that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("io.github.chrisshi.mom.data.entity..")
                .should().resideInAnyPackage("..infrastructure.persistence..")
                .because("Entity 是数据库行模型，不能进入 Application、Domain、Web 或公开 API")
                .check(productionClasses);
    }

    /** System 普通可变业务表使用完整 BaseEntity，不可变发布快照精确使用审计基类。 */
    @Test
    void systemEntitiesMustSelectBaseClassByCapability() {
        classes()
                .that().resideInAnyPackage("io.github.chrisshi.mom.system.infrastructure.persistence..")
                .and().haveSimpleNameEndingWith("Entity")
                .and().doNotHaveFullyQualifiedName(SystemI18nReleaseEntity.class.getName())
                .should().beAssignableTo(BaseEntity.class)
                .because("System 可更新且支持乐观锁/逻辑删除的普通业务表使用 BaseEntity")
                .check(productionClasses);
        assertThat(BaseAuditEntity.class.isAssignableFrom(SystemI18nReleaseEntity.class)).isTrue();
        assertThat(BaseEntity.class.isAssignableFrom(SystemI18nReleaseEntity.class)).isFalse();
    }

    /** MOM 业务代码不得把 MyBatis-Plus 通用 Service 当作领域或 Repository 契约。 */
    @Test
    void businessCodeMustNotUseMybatisPlusGenericServices() {
        noClasses()
                .that().resideInAnyPackage("io.github.chrisshi.mom..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.extension.service..")
                .because("IService/ServiceImpl 泄漏 ORM CRUD，不表达 MOM 用例或持久化语义")
                .check(productionClasses);
    }

    /**
     * 正式 bounded context 默认且强制使用 MyBatis-Plus；精确例外只覆盖 SAS 官方 Store 与既有技术探针。
     */
    @Test
    void boundedContextsMustNotIntroduceDirectJdbcAccess() {
        noClasses()
                .that().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam..",
                        "io.github.chrisshi.mom.mdm..",
                        "io.github.chrisshi.mom.integration..",
                        "io.github.chrisshi.mom.system..",
                        "io.github.chrisshi.mom.mes..",
                        "io.github.chrisshi.mom.wms..",
                        "io.github.chrisshi.mom.qms..",
                        "io.github.chrisshi.mom.ems..",
                        "io.github.chrisshi.mom.eam..",
                        "io.github.chrisshi.mom.traceability..")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.security.IamAuthorizationServerProtocolConfiguration")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.mdm.application.MdmSeataAtLocalParticipantService")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.integration.application.IntegrationSeataAtParticipantService")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.integration.messaging.IntegrationDomainEventConsumerConfiguration")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "java.sql..")
                .because("正式业务表统一使用 MyBatis-Plus；直接 JDBC 必须先登记精确协议或技术例外")
                .check(productionClasses);
    }

    /** 数据库时间点使用 Instant，不能用 LocalDateTime 隐去时区语义。 */
    @Test
    void persistenceEntitiesMustNotUseLocalDateTime() {
        noClasses()
                .that().resideInAnyPackage("..infrastructure.persistence..")
                .and().haveSimpleNameEndingWith("Entity")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.time.LocalDateTime")
                .because("全球时间点统一使用 Instant/timestamptz")
                .check(productionClasses);
    }

    /** 公共 Entity 基类必须保持 String ID、Long Version 与 Boolean 逻辑删除能力。 */
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
