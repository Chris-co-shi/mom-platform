package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase01TechnicalProbeRetirementArchitectureTest {

    private static final List<String> FORBIDDEN = List.of(
            "MdmDataProbe",
            "MdmOutboxProbe",
            "MdmSeataAt",
            "MdmServiceProbe",
            "IntegrationMdmProbe",
            "IntegrationSeataAtParticipant",
            "/internal/mdm/probe",
            "/internal/mdm/data-probes",
            "/internal/mdm/outbox-probes",
            "/internal/mdm/seata-at-probes",
            "mom-integration-client",
            "spring-cloud-starter-stream-rocketmq",
            "<artifactId>mom-seata</artifactId>",
            "<artifactId>mom-outbox</artifactId>");

    @Test
    void mdmAndCoupledIntegrationRuntimeMustNotReintroducePhase01Probes() throws IOException {
        Path root = repositoryRoot();
        List<Path> roots = List.of(
                root.resolve("mom-mdm-platform"),
                root.resolve("mom-integration-platform/mom-integration-api/src/main/java"),
                root.resolve("mom-integration-platform/mom-integration-client/src/main/java"),
                root.resolve("mom-integration-platform/mom-integration-server/src/main/java"),
                root.resolve("mom-mdm-platform/mom-mdm-server/pom.xml"),
                root.resolve("mom-integration-platform/mom-integration-server/pom.xml"));
        List<String> violations = new ArrayList<>();
        for (Path scope : roots) {
            if (!Files.exists(scope)) {
                continue;
            }
            if (Files.isRegularFile(scope)) {
                inspect(scope, violations);
                continue;
            }
            try (var paths = Files.walk(scope)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.toString().contains("/src/test/"))
                        .filter(path -> !path.toString().contains("/db/migration/"))
                        .forEach(path -> inspect(path, violations));
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static void inspect(Path path, List<String> violations) {
        String name = path.getFileName().toString();
        if (!(name.endsWith(".java") || name.endsWith(".xml") || name.equals("pom.xml"))) {
            return;
        }
        try {
            String text = Files.readString(path);
            FORBIDDEN.stream()
                    .filter(text::contains)
                    .forEach(token -> violations.add(path + ": retired Phase 01 token: " + token));
        } catch (IOException exception) {
            violations.add(path + ": cannot read: " + exception.getMessage());
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("mom-mdm-platform"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("mom-platform repository root not found");
    }
}
