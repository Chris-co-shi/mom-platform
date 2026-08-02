package io.github.chrisshi.mom.architecture;

import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.iam.domain.role.IamRoleRepository;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.MybatisIamRoleRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.MybatisIamUserAccountRepository;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** IAM Admin 分层、旧包退出与 MyBatis-Plus Adapter 门禁。 */
class IamAdminLayerArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom.iam");

    @Test
    void applicationMustDependOnPortsAndDomainInsteadOfAdapters() {
        noClasses()
                .that().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam.application.admin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam.infrastructure..",
                        "io.github.chrisshi.mom.iam.web..",
                        "org.springframework.security..",
                        "org.springframework.web..",
                        "jakarta.servlet..")
                .because("IAM Admin Application 只能编排领域对象与框架无关 Port")
                .check(classes);
    }

    @Test
    void webMustOnlyEnterThroughApplication() {
        noClasses()
                .that().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam.web.admin..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.chrisshi.mom.iam.infrastructure..",
                        "io.github.chrisshi.mom.iam.domain..")
                .because("IAM Admin Web 只能调用 Application")
                .check(classes);
    }

    @Test
    void legacyMixedAdminPackageMustBeEmpty() {
        assertThat(classes.stream()
                .filter(type -> type.getPackageName()
                        .startsWith("io.github.chrisshi.mom.iam.admin"))
                .map(type -> type.getName())
                .toList()).isEmpty();
    }

    @Test
    void singleTableUserAndRoleAdaptersMustUseCrudRepositoryAndDomainPorts() {
        assertThat(CrudRepository.class)
                .isAssignableFrom(MybatisIamUserAccountRepository.class);
        assertThat(IamUserAccountRepository.class)
                .isAssignableFrom(MybatisIamUserAccountRepository.class);
        assertThat(CrudRepository.class)
                .isAssignableFrom(MybatisIamRoleRepository.class);
        assertThat(IamRoleRepository.class)
                .isAssignableFrom(MybatisIamRoleRepository.class);
    }
}
