package io.github.chrisshi.mom.iam;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * IAM 数据访问架构门禁。
 *
 * <p>门禁把 Spring Authorization Server 官方 JDBC Store 限定在唯一协议配置类，并阻止管理、
 * 应用、领域和 MOM Repository 重新依赖 Spring JDBC。它同时验证 Mapper 基类、Application
 * Service/Controller 依赖方向以及 MyBatis-Plus Service 抽象禁令。</p>
 */
class IamPersistenceArchitectureTest {

    private static final String AUTHORIZATION_SERVER_CONFIGURATION =
            "io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration";

    private final JavaClasses iamClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom.iam");

    @Test
    void onlyOfficialAuthorizationServerAdapterMayDependOnSpringJdbc() {
        noClasses()
                .that().doNotHaveFullyQualifiedName(AUTHORIZATION_SERVER_CONFIGURATION)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..")
                .because("MOM 自有 IAM 持久化必须统一通过 MyBatis Mapper 与明确用途 Repository")
                .check(iamClasses);
    }

    @Test
    void applicationServicesAndControllersMustNotDependOnMappers() {
        noClasses()
                .that().haveSimpleNameEndingWith("Service")
                .or().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure.persistence.mapper..")
                .because("Application Service 与 Controller 只能依赖明确用途 Repository")
                .check(iamClasses);
    }

    @Test
    void domainMustNotDependOnMyBatisPlus() {
        noClasses()
                .that().resideInAnyPackage("..iam.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("com.baomidou.mybatisplus..")
                .because("Domain 必须保持持久化框架无关")
                .check(iamClasses);
    }

    @Test
    void mappersMustUseMomBaseMapperAndMustNotIntroduceServiceAbstractions() {
        classes()
                .that().areAnnotatedWith(Mapper.class)
                .should().beAssignableTo(MomBaseMapper.class)
                .because("MOM Mapper 必须统一继承禁止 Wrapper-only Update 的 MomBaseMapper")
                .check(iamClasses);

        noClasses()
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.baomidou.mybatisplus.extension.service.IService")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl")
                .because("IAM 禁止通过 IService 或 ServiceImpl 暴露万能 CRUD")
                .check(iamClasses);
    }
}
