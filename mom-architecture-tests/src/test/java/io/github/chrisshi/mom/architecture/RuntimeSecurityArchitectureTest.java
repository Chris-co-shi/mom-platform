package io.github.chrisshi.mom.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 运行时安全、OpenFeign 与 Redis 依赖边界的渐进式 ArchUnit 门禁。
 *
 * <p>本测试只分析 Reactor 已编译生产字节码，不扫描 Java import。Domain、API、Web、Application 和
 * {@code mom-data} 规则全量生效；Feign 与 Redis 的允许位置对应当前明确 Adapter/Framework 边界，
 * 没有按模块或包隐藏历史违规。新增例外必须精确到类并先登记到 S03 历史清单。</p>
 *
 * <p>测试不启动网络、Redis、IAM 或安全协议端点；违反边界时直接失败，不改变运行时失败策略。</p>
 */
class RuntimeSecurityArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** Domain 不得依赖 Feign、Redis 或 Spring Security 运行时。 */
    @Test
    void domainMustNotDependOnSecurityOrRemoteInfrastructure() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.cloud.openfeign..",
                        "feign..",
                        "org.springframework.data.redis..",
                        "org.springframework.security..")
                .because("Domain 必须与认证上下文、远端传输和 Redis 基础设施解耦")
                .check(productionClasses);
    }

    /** 公开 API 模块的包不得声明 Feign Adapter。 */
    @Test
    void publicApiMustNotContainFeignClients() {
        noClasses()
                .that().resideInAnyPackage("..api..")
                .should().beAnnotatedWith(FeignClient.class)
                .because("API 只定义稳定契约，Feign 属于调用方 Adapter")
                .check(productionClasses);
    }

    /** Feign Client 只能位于 client 或明确 Infrastructure Adapter；当前允许没有任何真实调用方。 */
    @Test
    void feignClientsMustStayInClientOrInfrastructure() {
        classes()
                .that().areAnnotatedWith(FeignClient.class)
                .should().resideInAnyPackage("..client..", "..infrastructure..")
                .because("Feign 传输类型不得进入 Domain、Application、Web 或 API")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    /** Controller 不得直接操作 RedisTemplate。 */
    @Test
    void controllersMustNotDependOnRedisTemplates() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..")
                .because("Web 只能调用 Application，用例不得在 Controller 中直接读写 Redis")
                .check(productionClasses);
    }

    /** Application 不得直接操作 RedisTemplate 或 Spring SecurityContext。 */
    @Test
    void applicationMustNotDependOnRedisOrSecurityContext() {
        noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.redis..",
                        "org.springframework.security.core.context..")
                .because("Application 通过 Port 和显式调用主体工作，不读取基础设施上下文")
                .check(productionClasses);
    }

    /** 数据基础模块不得反向读取 SecurityContext。 */
    @Test
    void dataModuleMustNotReadSecurityContext() {
        noClasses()
                .that().resideInAnyPackage("io.github.chrisshi.mom.data..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
                .because("mom-data 依赖 CurrentActor 抽象，不能依赖 mom-security 的线程上下文")
                .check(productionClasses);
    }

    /** 直接 Redis 技术实现只能位于已冻结的 Framework、安全 Infrastructure 或 Configuration 边界。 */
    @Test
    void redisImplementationsMustStayInTechnicalBoundaries() {
        noClasses()
                .that().resideOutsideOfPackages(
                        "io.github.chrisshi.mom.idempotency..",
                        "io.github.chrisshi.mom.ratelimit..",
                        "io.github.chrisshi.mom.gateway.security..",
                        "io.github.chrisshi.mom.iam.security..",
                        "..infrastructure..",
                        "..configuration..",
                        "..autoconfigure..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..")
                .because("Redis 是技术 Adapter，不得渗入 Domain、Application 或 Web")
                .check(productionClasses);
    }
}
