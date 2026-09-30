package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.6 最终平台工程治理决策链与 Framework 非空门面门禁。 */
class PlatformEngineeringGovernanceTest {
    private static final Pattern SUPERSEDED = Pattern.compile("状态：Superseded by ADR-(\\d{3})");

    @Test
    void everyP16PhaseMustHaveAnAcceptedAdr() throws Exception {
        Path adrRoot = reactorRoot().resolve("docs/adr");
        for (int number : IntStream.rangeClosed(32, 39).toArray()) {
            try (var files = Files.list(adrRoot)) {
                Path adr = files.filter(path -> path.getFileName().toString()
                                .startsWith("ADR-%03d-".formatted(number)))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("缺少 ADR-%03d".formatted(number)));
                assertAcceptedOrSupersededByAccepted(adrRoot, adr);
            }
        }
    }

    /**
     * 接受仍有效的 Accepted ADR，或已明确指向另一个 Accepted ADR 的历史决策。
     *
     * <p>Superseded 不是治理失败，但替代链必须指向实际存在且已接受的后继决策，避免只修改状态文本
     * 绕过治理门禁。该方法只读取仓库文档，无外部副作用。</p>
     */
    private static void assertAcceptedOrSupersededByAccepted(Path adrRoot, Path adr) throws Exception {
        String content = Files.readString(adr);
        if (content.contains("状态：Accepted")) {
            return;
        }
        Matcher matcher = SUPERSEDED.matcher(content);
        assertThat(matcher.find()).as(adr.toString()).isTrue();
        String successorNumber = matcher.group(1);
        try (var files = Files.list(adrRoot)) {
            Path successor = files.filter(path -> path.getFileName().toString()
                            .startsWith("ADR-" + successorNumber + "-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("缺少替代 ADR-" + successorNumber));
            assertThat(Files.readString(successor)).as(successor.toString()).contains("状态：Accepted");
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
    void systemV1MustNotKeepObsoleteMessagingAcceptanceScriptOrConfiguration() throws Exception {
        assertThat(reactorRoot().resolve(
                ".github/scripts/system-rocketmq-runtime-event-smoke.sh")).doesNotExist();
        String systemConfiguration = Files.readString(reactorRoot().resolve(
                "mom-system-platform/mom-system-server/src/main/resources/application.yml"));
        assertThat(systemConfiguration).doesNotContain(
                "spring.cloud.stream", "RocketMQ", "outbox", "inbox");
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
