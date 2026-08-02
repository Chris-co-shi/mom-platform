package io.github.chrisshi.mom.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 正式 bounded context Server 的 Package 职责与依赖方向门禁。
 *
 * <p>本测试使用 Reactor 已编译字节码验证 S15-E 的 Adapter-first 决策。阶段一仅为当次迁移目标保留
 * 完整类名级例外；阶段二完成行为保持型移动后必须删除这些例外，不允许扩大为包级白名单。</p>
 *
 * <p>测试本身不创建 Spring Bean，也不访问外部基础设施，线程安全与事务行为不受影响；失败表示
 * 类的职责位置或依赖方向偏离 ADR-027，必须移动类型或提供精确 Accepted Exception。</p>
 */
class PackageLayoutArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** 持久化 Entity 必须集中在标准 Entity 职责包。 */
    @Test
    void boundedContextEntitiesMustResideInEntityPackage() {
        classes()
                .that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("io.github.chrisshi.mom.data.entity..")
                .should().resideInAnyPackage("..infrastructure.persistence.entity")
                .because("数据库行模型必须集中在 Entity 技术职责包")
                .check(productionClasses);
    }

    /** 普通 Mapper 必须集中在标准 Mapper 职责包。 */
    @Test
    void boundedContextMappersMustResideInMapperPackage() {
        classes()
                .that().areAssignableTo(MomBaseMapper.class)
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage("..infrastructure.persistence.mapper")
                .because("普通 Entity Mapper 必须集中在 Mapper 技术职责包")
                .check(productionClasses);
    }

    /** 专用 Query Mapper 必须与普通写模型 Mapper 分离。 */
    @Test
    void queryMappersMustResideInQueryPackage() {
        classes()
                .that().haveSimpleNameEndingWith("QueryMapper")
                .should().resideInAnyPackage("..infrastructure.persistence.query")
                .because("专用查询映射必须通过 Query Row 或 Projection 表达读取模型")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** MyBatis Repository Adapter 必须集中在标准 Repository 职责包。 */
    @Test
    void mybatisRepositoriesMustResideInRepositoryPackage() {
        classes()
                .that().haveSimpleNameStartingWith("Mybatis")
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAnyPackage("..infrastructure.persistence.repository")
                .because("Repository Adapter 必须隐藏 Mapper 与 ORM 细节")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Persistence 技术职责层不得继续建立 Parameter、Dictionary、I18n 等 Feature 烟囱。 */
    @Test
    void persistenceMustNotUseFeatureFirstPackagesForNewCode() {
        noClasses()
                .should().resideInAnyPackage(
                        "..infrastructure.persistence.parameter..",
                        "..infrastructure.persistence.dictionary..",
                        "..infrastructure.persistence.i18n..",
                        "..infrastructure.persistence.user..",
                        "..infrastructure.persistence.role..",
                        "..infrastructure.persistence.admin..")
                .because("Persistence 的第一组织维度是技术职责，而不是业务 Feature")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Entity 与 Mapper 不得反向依赖 Web 或 Application。 */
    @Test
    void entityAndMapperPackagesMustNotDependOnInboundLayers() {
        noClasses()
                .that().resideInAnyPackage(
                        "..infrastructure.persistence.entity..",
                        "..infrastructure.persistence.mapper..")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamOauthClientPolicyMapper")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..application..")
                .because("数据库写模型不能依赖入站协议或用例实现")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Repository 与 Query Adapter 不得依赖 Web。 */
    @Test
    void repositoryAndQueryPackagesMustNotDependOnWeb() {
        noClasses()
                .that().resideInAnyPackage(
                        "..infrastructure.persistence.repository..",
                        "..infrastructure.persistence.query..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..interfaces.rest..")
                .because("出站持久化 Adapter 不能依赖入站 HTTP Adapter")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Web 与 Application 均不得直接依赖普通 Mapper。 */
    @Test
    void webAndApplicationMustNotDependOnMappers() {
        noClasses()
                .that().resideInAnyPackage("..web..", "..application..")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.mdm.application.MdmDataProbeService")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.mdm.application.MdmOutboxProbeService")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.mdm.application.MdmSeataAtLocalParticipantService")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.mdm.application.MdmSeataAtProbeService")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.integration.application.IntegrationSeataAtParticipantService")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                .because("用例层只能通过 Port，Web 只能通过 Application")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Domain 不得依赖 Infrastructure。 */
    @Test
    void domainMustNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                .because("Domain 必须保持 Adapter 无关")
                .allowEmptyShould(true)
                .check(productionClasses);
    }
}
