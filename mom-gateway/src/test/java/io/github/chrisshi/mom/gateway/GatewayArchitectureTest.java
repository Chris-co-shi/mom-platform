package io.github.chrisshi.mom.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class GatewayArchitectureTest {

    @Test
    void gatewayMustRemainReactiveAndMustNotDependOnWebMvc() {
        JavaClasses gatewayClasses = new ClassFileImporter()
                .importPackages("io.github.chrisshi.mom.gateway");

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web.servlet..",
                        "io.github.chrisshi.mom.webmvc..",
                        "jakarta.servlet..",
                        "javax.servlet..")
                .because("MOM Gateway is a WebFlux application and must not pull in the servlet stack")
                .check(gatewayClasses);

        noClasses()
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.lang.ThreadLocal")
                .because("Reactor request processing must not store request context in ThreadLocal")
                .check(gatewayClasses);
    }
}
