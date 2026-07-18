package io.github.chrisshi.mom.bootstrap;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureBoundaryTest {

    @Test
    void gatewayMustRemainReactiveAndMustNotDependOnWebMvc() {
        JavaClasses gatewayClasses = new ClassFileImporter()
                .importPackages("io.github.chrisshi.mom.gateway");

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web.servlet..",
                        "io.github.chrisshi.mom.webmvc..")
                .because("MOM Gateway is a WebFlux application and must not pull in the servlet stack")
                .check(gatewayClasses);
    }

    @Test
    void platformMustNotContainPcsOrWcsImplementationDependencies() {
        JavaClasses platformClasses = new ClassFileImporter()
                .importPackages("io.github.chrisshi.mom");

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..pcs..", "..wcs..")
                .because("PCS and WCS are independent repositories and deployment boundaries")
                .check(platformClasses);
    }
}
