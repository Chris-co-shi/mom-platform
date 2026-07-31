package io.github.chrisshi.mom.architecture;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nReleaseEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserPreferenceEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserViewSettingEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemDictionaryRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemParameterRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOM 持久化类型位置、Repository 抽象、技术栈选择与基础 Entity 能力的架构门禁。
 *
 * <p>本测试分析 Reactor 已编译生产字节码。Domain Repository Port 必须保持框架无关；单表 CRUD
 * Adapter 可以在 Infrastructure 内部复用 MyBatis-Plus {@link CrudRepository}，但该能力不得进入
 * Domain、Application、Web 或公开 API。MyBatis-Plus {@code IService/ServiceImpl} 继续全面禁止。</p>
 *
 * <p>多 Mapper 聚合、Query/Projection、OAuth/SAS 协议存储和专用 SQL Adapter 不为形式统一强制继承
 * 单表 Repository。正式 bounded context 对 Spring JDBC/java.sql 的直接依赖仅保留 SAS 官方 Store 的
 * 精确协议例外。</p>
 */
class PersistenceArchitectureTest {

    private static final String[] BOUNDED_CONTEXT_PACKAGES = {
            "io.github.chrisshi.mom.iam..",
            "io.github.chrisshi.mom.mdm..",
            "io.github.chrisshi.mom.integration..",
            "io.github.chrisshi.mom.system..",
            "io.github.chrisshi.mom.mes..",
            "io.github.chrisshi.mom.wms..",
            "io.github.chrisshi.mom.qms..",
            "io.github.chrisshi.mom.ems..",
            "io.github.chrisshi.mom.eam..",
            "io.github.chrisshi.mom.traceability.."
    };

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

    /** System 普通可变业务表使用完整 BaseEntity；无逻辑删除的快照与 Preference 精确使用审计基类。 */
    @Test
    void systemEntitiesMustSelectBaseClassByCapability() throws NoSuchFieldException {
        classes()
                .that().resideInAnyPackage("io.github.chrisshi.mom.system.infrastructure.persistence..")
                .and().haveSimpleNameEndingWith("Entity")
                .and().doNotHaveFullyQualifiedName(SystemI18nReleaseEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemUserPreferenceEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemUserViewSettingEntity.class.getName())
                .should().beAssignableTo(BaseEntity.class)
                .because("System 可更新且支持乐观锁/逻辑删除的普通业务表使用 BaseEntity")
                .check(productionClasses);
        assertThat(BaseAuditEntity.class.isAssignableFrom(SystemI18nReleaseEntity.class)).isTrue();
        assertThat(BaseEntity.class.isAssignableFrom(SystemI18nReleaseEntity.class)).isFalse();
        assertAuditVersionedWithoutLogicalDelete(SystemUserPreferenceEntity.class);
        assertAuditVersionedWithoutLogicalDelete(SystemUserViewSettingEntity.class);
    }

    /** 验证显式版本化但禁止逻辑删除的 Entity 精确具备审计与乐观锁能力。 */
    private static void assertAuditVersionedWithoutLogicalDelete(Class<?> entityType)
            throws NoSuchFieldException {
        assertThat(BaseAuditEntity.class.isAssignableFrom(entityType)).isTrue();
        assertThat(BaseEntity.class.isAssignableFrom(entityType)).isFalse();
        assertThat(entityType.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(java.util.Arrays.stream(entityType.getDeclaredFields())
                .anyMatch(field -> field.getAnnotation(TableLogic.class) != null)).isFalse();
    }

    /** MyBatis-Plus 通用 Service 不得成为 MOM 业务 Service 或 Repository。 */
    @Test
    void boundedContextsMustNotUseMybatisPlusGenericServices() {
        noClasses()
                .that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.extension.service..")
                .because("IService/ServiceImpl 暴露 ORM CRUD，不能表达 MOM 用例或领域仓储语义")
                .check(productionClasses);
    }

    /** MyBatis-Plus Repository 抽象只能作为 Infrastructure Repository Adapter 的实现机制。 */
    @Test
    void mybatisPlusRepositoriesMustStayInsideInfrastructureRepository() {
        noClasses()
                .that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .and().resideOutsideOfPackage("..infrastructure.persistence.repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.extension.repository..")
                .because("IRepository/CrudRepository 不得泄漏到 Domain、Application、Web、API 或其他 Adapter")
                .check(productionClasses);
    }

    /** Domain Repository Port 不得依赖任何 MyBatis-Plus 类型。 */
    @Test
    void domainRepositoryPortsMustRemainFrameworkIndependent() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat().resideInAnyPackage("com.baomidou.mybatisplus..")
                .because("Domain Repository 是框架无关 Port，不是 ORM Repository 接口")
                .check(productionClasses);
    }

    /** Domain、Application 和 Web 不得依赖具体 MyBatis Repository Adapter。 */
    @Test
    void upperLayersMustNotDependOnConcreteMybatisRepositories() {
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..", "..web..", "..interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence.repository..")
                .because("上层只依赖 Domain Port，CrudRepository 的宽 CRUD 能力必须封闭在 Infrastructure")
                .check(productionClasses);
    }

    /** 当前明确的单表 Domain Port Adapter 必须复用 CrudRepository。 */
    @Test
    void approvedSingleTableAdaptersMustUseCrudRepository() {
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemParameterRepository.class)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemDictionaryRepository.class)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemDictionaryItemRepository.class)).isTrue();
    }

    /** 正式 bounded context 默认且强制使用 MyBatis-Plus；SAS 官方 Store 是唯一精确 JDBC 例外。 */
    @Test
    void boundedContextsMustNotIntroduceDirectJdbcAccess() {
        noClasses()
                .that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.security.IamAuthorizationServerProtocolConfiguration")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "java.sql..")
                .because("正式业务表统一使用 MyBatis-Plus；直接 JDBC 必须先登记精确协议例外")
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
