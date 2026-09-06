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
 * <p>当前按 ADR-042 验证渐进式分层：Level 1 允许 Application 直接使用本服务 Mapper/Entity，
 * 但 Controller 始终不得穿透到持久化实现；已经通过独立 ADR 建立 Domain/Port 边界的旧模块继续
 * 保留更严格规则。测试不要求所有服务目录完全对称，也不以空包证明架构完整。</p>
 *
 * <p>测试本身不创建 Spring Bean，也不访问外部基础设施；失败表示类型职责位置或依赖方向偏离
 * 当前工程规范，必须调整实现或提供精确 Accepted Exception。</p>
 */
class PackageLayoutArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** 持久化 Entity 可位于简化 Infrastructure 或复杂 Persistence 的 Entity 职责包。 */
    @Test
    void boundedContextEntitiesMustResideInEntityPackage() {
        classes()
                .that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("io.github.chrisshi.mom.data.entity..")
                .should().resideInAnyPackage(
                        "..infrastructure.entity",
                        "..infrastructure.persistence.entity")
                .because("数据库行模型必须位于明确的 Entity 技术职责包")
                .check(productionClasses);
    }

    /** 普通 Mapper 可位于简化 Infrastructure 或复杂 Persistence 的 Mapper 职责包。 */
    @Test
    void boundedContextMappersMustResideInMapperPackage() {
        classes()
                .that().areAssignableTo(MomBaseMapper.class)
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage(
                        "..infrastructure.mapper",
                        "..infrastructure.persistence.mapper")
                .because("普通 Entity Mapper 必须位于明确的 Mapper 技术职责包")
                .check(productionClasses);
    }

    /** 专用 Query Mapper 必须与普通写模型 Mapper 分离，但不强制预先创建 persistence 中间层。 */
    @Test
    void queryMappersMustResideInQueryPackage() {
        classes()
                .that().haveSimpleNameEndingWith("QueryMapper")
                .should().resideInAnyPackage(
                        "..infrastructure.query",
                        "..infrastructure.persistence.query")
                .because("专用查询映射必须通过明确 Query 职责包表达读取模型")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** MyBatis Repository Adapter 只在真实存在时进入 Repository 职责包。 */
    @Test
    void mybatisRepositoriesMustResideInRepositoryPackage() {
        classes()
                .that().haveSimpleNameStartingWith("Mybatis")
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAnyPackage(
                        "..infrastructure.repository",
                        "..infrastructure.persistence.repository")
                .because("已经引入的 Repository Adapter 必须位于明确技术职责包")
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

    /** Entity 与 Mapper 不得反向依赖 Controller/Web 或 Application。 */
    @Test
    void entityAndMapperPackagesMustNotDependOnInboundLayers() {
        noClasses()
                .that().resideInAnyPackage(
                        "..infrastructure.entity..",
                        "..infrastructure.mapper..",
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
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..controller..", "..web..", "..application..")
                .because("数据库写模型不能反向依赖入站协议或用例实现")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Repository 与 Query Adapter 不得依赖 Controller/Web。 */
    @Test
    void repositoryAndQueryPackagesMustNotDependOnWeb() {
        noClasses()
                .that().resideInAnyPackage(
                        "..infrastructure.repository..",
                        "..infrastructure.query..",
                        "..infrastructure.persistence.repository..",
                        "..infrastructure.persistence.query..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..controller..", "..web..", "..interfaces.rest..")
                .because("出站持久化实现不能反向依赖入站 HTTP 边界")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Controller/Web 在所有架构层级都不得直接依赖普通 Mapper。 */
    @Test
    void controllersMustNotDependOnMappers() {
        noClasses()
                .that().resideInAnyPackage("..controller..", "..web..", "..interfaces.rest..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                .because("Controller/Web 只能通过 Application 进入业务")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /**
     * 已经通过历史 ADR 建立 Level 2/3 边界的模块继续禁止 Application 直接依赖 Mapper。
     * Mini Auth 等明确 Level 1 模块不在此规则中，允许 Application 直接编排本服务 Mapper/Entity。
     */
    @Test
    void domainBackedApplicationsMustNotDependOnMappers() {
        noClasses()
                .that().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam.application..",
                        "io.github.chrisshi.mom.system.application..",
                        "io.github.chrisshi.mom.mdm.application..",
                        "io.github.chrisshi.mom.integration.application..",
                        "io.github.chrisshi.mom.mes.application..",
                        "io.github.chrisshi.mom.wms.application..",
                        "io.github.chrisshi.mom.qms.application..",
                        "io.github.chrisshi.mom.ems.application..",
                        "io.github.chrisshi.mom.eam.application..",
                        "io.github.chrisshi.mom.traceability.application..")
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
                .because("已经进入 Level 2/3 的 bounded context 继续遵守其既有 Port/Domain 边界")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Domain 一旦存在，就不得依赖 Infrastructure。 */
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