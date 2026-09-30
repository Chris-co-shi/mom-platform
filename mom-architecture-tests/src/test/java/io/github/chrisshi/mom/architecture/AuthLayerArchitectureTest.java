package io.github.chrisshi.mom.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mini Auth 当前 Level 1 分层与已删除 IAM 实现的退出门禁。
 *
 * <p>该测试分析聚合模块依赖中已编译的生产字节码。Auth Controller 只能进入 Application，Application
 * 可以按 ADR-042 直接编排本模块 Mapper/Entity，但不得反向依赖 HTTP；数据库 Mapper 必须继续复用
 * MomBaseMapper。测试无共享状态、不访问外部基础设施，架构违规时直接失败。</p>
 */
class AuthLayerArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** Auth Controller 不得穿透到 Infrastructure 数据库或安全实现。 */
    @Test
    void authControllersMustOnlyEnterThroughApplication() {
        noClasses()
                .that().resideInAnyPackage("io.github.chrisshi.mom.auth.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.chrisshi.mom.auth.infrastructure..")
                .because("Mini Auth Controller 只能通过 Application 进入用例")
                .check(classes);
    }

    /** Auth Application 不得依赖 Controller、Servlet 或 Spring MVC 协议类型。 */
    @Test
    void authApplicationMustRemainHttpIndependent() {
        noClasses()
                .that().resideInAnyPackage("io.github.chrisshi.mom.auth.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.chrisshi.mom.auth.controller..",
                        "org.springframework.web..", "jakarta.servlet..")
                .because("Level 1 只放宽本地持久化依赖，不允许 Application 感知 HTTP")
                .check(classes);
    }

    /** Auth 的普通数据库 Mapper 必须继续使用 MOM 统一 Mapper 能力。 */
    @Test
    void authMappersMustUseMomBaseMapper() {
        classes()
                .that().resideInAnyPackage("io.github.chrisshi.mom.auth.infrastructure.mapper..")
                .and().haveSimpleNameEndingWith("Mapper")
                .should().beAssignableTo(MomBaseMapper.class)
                .because("Mini Auth 单表访问必须保留统一 MyBatis-Plus 安全边界")
                .check(classes);
    }

    /** 已从 Reactor 删除的旧 IAM 包不得通过残留依赖重新进入运行时。 */
    @Test
    void retiredIamPackagesMustRemainAbsent() {
        assertThat(classes.stream()
                .filter(type -> type.getPackageName().startsWith("io.github.chrisshi.mom.iam"))
                .map(type -> type.getName())
                .toList()).isEmpty();
    }
}
