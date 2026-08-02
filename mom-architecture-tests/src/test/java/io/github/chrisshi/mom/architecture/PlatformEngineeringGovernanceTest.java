package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.6 最终平台工程治理决策链与 Framework 非空门面门禁。 */
class PlatformEngineeringGovernanceTest {

    @Test
    void everyP16PhaseMustHaveAnAcceptedAdr() throws Exception {
        Path adrRoot = reactorRoot().resolve("docs/adr");
        for (int number : IntStream.rangeClosed(32, 39).toArray()) {
            try (var files = Files.list(adrRoot)) {
                Path adr = files.filter(path -> path.getFileName().toString()
                                .startsWith("ADR-%03d-".formatted(number)))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("缺少 ADR-%03d".formatted(number)));
                assertThat(Files.readString(adr)).as(adr.toString()).contains("状态：Accepted");
            }
        }
    }

    @Test
    void frameworkMustKeepOwnedModulesWithoutSpeculativeFacades() throws Exception {
        String pom = Files.readString(reactorRoot().resolve("mom-framework/pom.xml"));

        assertThat(pom)
                .contains("<module>mom-cache</module>", "<module>mom-messaging</module>",
                        "<module>mom-outbox</module>", "<module>mom-resilience</module>")
                .doesNotContain("<module>mom-event</module>", "<module>mom-data-access</module>");
    }

    @Test
    void legacyCacheRemovalMustRequireProductionEvidenceAndMajorCleanup() throws Exception {
        String adr = Files.readString(reactorRoot().resolve(
                "docs/adr/ADR-032-Cache-Region与Factory-Scope兼容迁移.md"));

        assertThat(adr).contains(
                "全仓生产源码零调用",
                "连续两个正式 Release 周期均为零",
                "生产 Prometheus 查询或截图",
                "Removal ADR Accepted",
                "后续 Major Cleanup 删除");
    }

    @Test
    void systemRocketMqSmokeMustAssertTypedVersionedKeysWithoutRedisIndex() throws Exception {
        String smoke = Files.readString(reactorRoot().resolve(
                ".github/scripts/system-rocketmq-runtime-event-smoke.sh"));

        assertThat(smoke)
                .contains("mom:${ENVIRONMENT}:_global:system:cache:v1:parameter-resolved")
                .doesNotContain("parameter-resolved-index", "redis-cli SMEMBERS", "redis-cli KEYS");
        String systemConfiguration = Files.readString(reactorRoot().resolve(
                "mom-system-platform/mom-system-server/src/main/resources/application.yml"));
        assertThat(systemConfiguration).contains("""
                systemRuntimeChangeConsumer-in-0:
                          destination: ${SYSTEM_RUNTIME_EVENT_TOPIC:mom-system-runtime-events-v1}
                          group: ${SYSTEM_RUNTIME_EVENT_CONSUMER_GROUP:mom-system-runtime-cache-invalidation-v1}
                          content-type: application/json
                          consumer:
                            max-attempts: 1
                            use-native-decoding: true
                """);
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
