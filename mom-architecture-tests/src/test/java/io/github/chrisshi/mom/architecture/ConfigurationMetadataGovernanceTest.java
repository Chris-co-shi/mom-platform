package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.6 Configuration Metadata 根索引、字段完整性与参数可覆盖门禁。 */
class ConfigurationMetadataGovernanceTest {

    @Test
    void rootIndexMustReferenceEveryGovernedModuleMetadata() throws Exception {
        Path root = reactorRoot();
        String index = Files.readString(root.resolve("config-metadata.yml"));
        List<String> metadataPaths = List.of(
                "mom-framework/mom-cache/src/main/resources/config-metadata.yml",
                "mom-framework/mom-resilience/src/main/resources/config-metadata.yml",
                "mom-system-platform/mom-system-server/src/main/resources/config-metadata.yml");

        assertThat(index).contains("owner:", "failureMode:", "changeImpact:");
        for (String metadataPath : metadataPaths) {
            assertThat(index).contains(metadataPath);
            assertThat(root.resolve(metadataPath)).isRegularFile();
        }
    }

    @Test
    void everyPropertyMustDeclareOperationalGovernanceFields() throws Exception {
        Path root = reactorRoot();
        for (String relative : List.of(
                "mom-framework/mom-cache/src/main/resources/config-metadata.yml",
                "mom-system-platform/mom-system-server/src/main/resources/config-metadata.yml")) {
            String content = Files.readString(root.resolve(relative));
            List<String> entries = propertyEntries(content);
            assertThat(entries).as(relative).isNotEmpty();
            assertThat(entries).allSatisfy(entry -> {
                assertThat(entry).contains(
                        "environmentVariable:", "type:", "default:", "sensitive:", "owner:",
                        "failureMode:", "restartRequired:", "changeImpact:");
                assertThat(entry).containsAnyOf("required:", "requiredWhen:");
            });
        }
    }

    @Test
    void resilienceMetadataMustFreezeStructureButNeverNumericValues() throws Exception {
        String content = Files.readString(reactorRoot().resolve(
                "mom-framework/mom-resilience/src/main/resources/config-metadata.yml"));

        assertThat(content)
                .contains("overridable: true", "valuesFrozen: false", "default", "system-query")
                .doesNotContain("slidingWindowSize:", "failureRateThreshold:", "timeoutDuration:",
                        "maxConcurrentCalls:");
    }

    private static List<String> propertyEntries(String content) {
        String[] parts = content.split("(?m)^  - key: ");
        return java.util.Arrays.stream(parts).skip(1).map(value -> "  - key: " + value).toList();
    }

    private static Path reactorRoot() {
        Path candidate = Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        while (candidate != null) {
            Path pom = candidate.resolve("pom.xml");
            try {
                if (Files.isRegularFile(pom)) {
                    String content = Files.readString(pom);
                    if (content.contains("<artifactId>mom-platform</artifactId>")
                            && content.contains("<modules>")) {
                        return candidate;
                    }
                }
            }
            catch (java.io.IOException exception) {
                throw new IllegalStateException("无法读取 Reactor POM", exception);
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("无法定位 MOM Reactor 根目录");
    }
}
