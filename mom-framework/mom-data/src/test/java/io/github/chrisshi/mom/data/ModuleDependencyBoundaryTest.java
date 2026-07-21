package io.github.chrisshi.mom.data;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1.5 S01 CurrentActor 模块依赖方向门禁。 */
class ModuleDependencyBoundaryTest {

    @Test
    void coreDataAndSecurityMustKeepFrozenDependencyDirection() throws Exception {
        Path root = locateRoot();
        String corePom = Files.readString(root.resolve("mom-framework/mom-core/pom.xml"));
        String dataPom = Files.readString(root.resolve("mom-framework/mom-data/pom.xml"));
        String securityPom = Files.readString(root.resolve("mom-framework/mom-security/pom.xml"));
        assertFalse(corePom.contains("spring-boot-starter-security"));
        assertFalse(corePom.contains("mybatis-plus-spring-boot4-starter"));
        assertFalse(dataPom.contains("<artifactId>mom-security</artifactId>"));
        assertTrue(dataPom.contains("<artifactId>mom-core</artifactId>"));
        assertTrue(securityPom.contains("<artifactId>mom-core</artifactId>"));
    }

    private static Path locateRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("mom-framework"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate MOM Platform reactor root");
    }
}
