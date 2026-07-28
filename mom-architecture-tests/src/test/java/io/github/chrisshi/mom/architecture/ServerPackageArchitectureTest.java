package io.github.chrisshi.mom.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * MOM Server 包分层的渐进式 ArchUnit 门禁。
 *
 * <p>测试分析聚合测试模块依赖中的已编译生产字节码，不使用源码正则。Domain、Controller、公开 API
 * 与 Gateway 规则全量生效；Application 规则仅排除五个已在 S01 规范逐文件登记的 Phase 01 技术探针，
 * 防止历史技术验证代码迫使本 Slice 扩大为生产重构。新增代码不能加入该精确基线。</p>
 */
class ServerPackageArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** Domain 不得反向依赖 Web、Infrastructure 或具体技术实现。 */
    @Test
    void domainMustRemainFrameworkAndAdapterIndependent() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..web..", "..interfaces.rest..", "..infrastructure..",
                        "org.springframework.web..", "org.springframework.jdbc..",
                        "com.baomidou.mybatisplus..", "org.apache.ibatis..",
                        "org.springframework.cloud.openfeign..", "org.springframework.data.redis..",
                        "jakarta.servlet..")
                .because("Domain 必须保持框架和入出站 Adapter 无关")
                .check(productionClasses);
    }

    /** 非基线 Application 代码不得依赖 HTTP、Web DTO、Mapper、Entity 或 JDBC。 */
    @Test
    void applicationMustNotDependOnWebOrPersistenceDetails() {
        noClasses()
                .that().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam.application..",
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
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..web..", "..interfaces.rest..", "..infrastructure.persistence..",
                        "org.springframework.web..", "org.springframework.jdbc..", "jakarta.servlet..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Entity")
                .because("Application 只能通过 Command/Query 与 Port 编排用例")
                .check(productionClasses);
    }

    /** Controller 不得直接依赖 Mapper、Repository 或数据库 Entity。 */
    @Test
    void controllersMustNotDependDirectlyOnPersistenceTypes() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.web.IamDirectAuthenticationController")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.persistence..")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("Controller 只负责 HTTP 协议适配并调用 Application 用例")
                .check(productionClasses);
    }

    /** 公开 API 包不得暴露 Web、持久化或自动配置实现。 */
    @Test
    void publicApiPackagesMustRemainStableContracts() {
        noClasses()
                .that().resideInAnyPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "org.springframework.boot.autoconfigure..",
                        "com.baomidou.mybatisplus..", "org.apache.ibatis..", "org.springframework.jdbc..",
                        "jakarta.servlet..")
                .orShould().haveSimpleNameEndingWith("Entity")
                .orShould().haveSimpleNameEndingWith("Mapper")
                .orShould().haveSimpleNameEndingWith("Repository")
                .orShould().haveSimpleNameEndingWith("Controller")
                .orShould().haveSimpleNameEndingWith("Configuration")
                .because("API 模块只承载跨服务稳定契约")
                .check(productionClasses);
    }

    /** Gateway 生产代码不得依赖 Servlet 或 Spring MVC。 */
    @Test
    void gatewayMustRemainWebFluxOnly() {
        noClasses()
                .that().resideInAnyPackage("io.github.chrisshi.mom.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", "org.springframework.web.servlet..",
                        "io.github.chrisshi.mom.webmvc..")
                .because("Gateway 必须保持 WebFlux 和 Reactor 运行模型")
                .check(productionClasses);
    }
}
