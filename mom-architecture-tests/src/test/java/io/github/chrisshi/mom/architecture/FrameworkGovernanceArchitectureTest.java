package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.6 Framework Freeze 的源码级基础设施依赖适应度函数。
 *
 * <p>测试读取所有 {@code src/main/java}，只解析明确 import、注解和类型名，不扫描测试源码。历史例外必须是
 * 完整文件路径，禁止目录或通配符白名单。当前两条 System Redis 例外只服务 ADR-037 迁移，Phase 6 完成后
 * 必须从列表删除；SAS JDBC 是协议边界，Outbox JDBC 是 Framework Adapter 边界。</p>
 */
class FrameworkGovernanceArchitectureTest {

    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+([^;]+);");
    private static final Pattern CONTEXT_PATH = Pattern.compile("^mom-([a-z0-9-]+)-platform/");
    private static final Set<String> TEMPORARY_SYSTEM_CACHE_EXCEPTIONS = Set.of(
            "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/"
                    + "infrastructure/cache/redis/RedisSystemRuntimeCacheAdapter.java",
            "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/"
                    + "infrastructure/cache/redis/RedisSystemI18nRuntimeCacheAdapter.java"
    );
    private static final String SAS_JDBC_EXCEPTION =
            "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/security/"
                    + "IamAuthorizationServerProtocolConfiguration.java";

    /** System/IAM 与业务层不得直接拥有通用 Cache/JDBC/Feign/Messaging 基础设施。 */
    @Test
    void businessProductionSourcesMustUseFrameworkAdaptersOrExactProtocolExceptions() throws Exception {
        List<String> violations = new ArrayList<>();
        for (SourceFile source : productionSources()) {
            Set<String> imports = source.imports();
            boolean systemServer = source.path().startsWith("mom-system-platform/mom-system-server/");
            boolean iamServer = source.path().startsWith("mom-iam-platform/mom-iam-server/");
            boolean businessServer = source.path().matches("mom-[^/]+-platform/mom-[^/]+-server/.*");
            boolean domainOrApplication = source.path().contains("/domain/")
                    || source.path().contains("/application/");

            if (systemServer && imports.stream().anyMatch(FrameworkGovernanceArchitectureTest::isRedisOrCaffeine)
                    && !TEMPORARY_SYSTEM_CACHE_EXCEPTIONS.contains(source.path())) {
                violations.add(source.path() + " System Server 直接依赖 Redis/Caffeine");
            }
            if (iamServer && imports.stream().anyMatch(name -> name.startsWith("com.github.benmanes.caffeine"))) {
                violations.add(source.path() + " IAM Server 直接依赖 Caffeine");
            }
            if (businessServer && imports.stream().anyMatch(FrameworkGovernanceArchitectureTest::isJdbcTemplate)
                    && !SAS_JDBC_EXCEPTION.equals(source.path())) {
                violations.add(source.path() + " 业务 Server 直接依赖 JDBC Template");
            }
            if (domainOrApplication && imports.stream().anyMatch(
                    FrameworkGovernanceArchitectureTest::isForbiddenBusinessLayerInfrastructure)) {
                violations.add(source.path() + " Domain/Application 直接依赖基础设施实现");
            }
        }
        assertThat(violations).as("业务生产源码基础设施依赖越界").isEmpty();
    }

    /** StreamBridge 与 Outbox JDBC 只能由各自 Framework Adapter 持有。 */
    @Test
    void frameworkTechnologyDependenciesMustRemainInOwningAdapters() throws Exception {
        List<String> violations = new ArrayList<>();
        for (SourceFile source : productionSources()) {
            Set<String> imports = source.imports();
            if (imports.contains("org.springframework.cloud.stream.function.StreamBridge")
                    && !source.path().startsWith("mom-framework/mom-messaging/")) {
                violations.add(source.path() + " StreamBridge 只能存在于 mom-messaging");
            }
            if (source.path().startsWith("mom-framework/")
                    && imports.stream().anyMatch(FrameworkGovernanceArchitectureTest::isJdbcTemplate)
                    && !source.path().startsWith("mom-framework/mom-outbox/")) {
                violations.add(source.path() + " Framework JDBC Template 只能存在于 mom-outbox 精确 Adapter");
            }
        }
        assertThat(violations).as("Framework Adapter 技术所有权越界").isEmpty();
    }

    /** 业务 Event Enum 不得跨 bounded context import；只允许共享 mom-messaging EventType。 */
    @Test
    void businessEventEnumsMustNotCrossBoundedContexts() throws Exception {
        List<String> violations = new ArrayList<>();
        for (SourceFile source : productionSources()) {
            String owner = contextOf(source.path());
            if (owner == null) {
                continue;
            }
            for (String imported : source.imports()) {
                if (!imported.endsWith("EventType")
                        || imported.equals("io.github.chrisshi.mom.messaging.event.EventType")) {
                    continue;
                }
                Matcher importedContext = Pattern.compile(
                        "^io\\.github\\.chrisshi\\.mom\\.([a-z0-9]+)\\..*EventType$")
                        .matcher(imported);
                if (importedContext.matches() && !owner.equals(importedContext.group(1))) {
                    violations.add(source.path() + " 跨 Context 引用 " + imported);
                }
            }
        }
        assertThat(violations).as("业务 Event Enum 必须归本 Context 所有").isEmpty();
    }

    /** 新业务生产代码不得继续调用 Legacy Cache API，也不得把权限决策类型送入 CacheService。 */
    @Test
    void businessSourcesMustUseTypedCacheAndNeverCacheAuthorizationDecisions() throws Exception {
        List<String> violations = new ArrayList<>();
        Set<String> legacyTypes = Set.of(
                "io.github.chrisshi.mom.cache.api.CacheType",
                "io.github.chrisshi.mom.cache.api.CacheKey",
                "io.github.chrisshi.mom.cache.api.CachePolicy");
        for (SourceFile source : productionSources()) {
            if (!source.path().startsWith("mom-") || !source.path().contains("-platform/")) {
                continue;
            }
            if (source.imports().stream().anyMatch(legacyTypes::contains)) {
                violations.add(source.path() + " 调用 Legacy Cache API");
            }
            String code = stripComments(source.content()).replaceAll("[^A-Za-z]", "").toLowerCase();
            if (source.content().contains("CacheService")
                    && (code.contains("authorizationdecision")
                    || code.contains("permissionevaluationresult")
                    || code.contains("allowdenydecision"))) {
                violations.add(source.path() + " 尝试缓存最终权限决策");
            }
        }
        assertThat(violations).as("Typed Cache 与权限决策禁缓存门禁").isEmpty();
    }

    /** Feign Adapter 必须同时声明 Propagation.NEVER 与运行时事务 Guard。 */
    @Test
    void feignAdaptersMustFailBeforeRemoteCallWhenTransactionIsActive() throws Exception {
        List<String> violations = productionSources().stream()
                .filter(source -> source.path().contains("/infrastructure/client/"))
                .filter(source -> source.imports().stream().anyMatch(name -> name.contains(".client.")))
                .filter(source -> source.content().contains("@Transactional"))
                .filter(source -> !source.content().contains("Propagation.NEVER")
                        || !source.content().contains("ResilienceTransactionGuard"))
                .map(source -> source.path() + " Feign Adapter 缺少 NEVER 或 Resilience Transaction Guard")
                .toList();
        assertThat(violations).as("事务内部远程调用门禁").isEmpty();
    }

    private static boolean isRedisOrCaffeine(String name) {
        return name.startsWith("org.springframework.data.redis")
                || name.startsWith("com.github.benmanes.caffeine");
    }

    private static boolean isJdbcTemplate(String name) {
        return name.equals("org.springframework.jdbc.core.JdbcTemplate")
                || name.equals("org.springframework.jdbc.core.simple.JdbcClient")
                || name.equals("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");
    }

    private static boolean isForbiddenBusinessLayerInfrastructure(String name) {
        return isRedisOrCaffeine(name)
                || isJdbcTemplate(name)
                || name.startsWith("feign.")
                || name.startsWith("org.springframework.cloud.openfeign")
                || name.equals("org.springframework.cloud.stream.function.StreamBridge");
    }

    private static String contextOf(String path) {
        Matcher matcher = CONTEXT_PATH.matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<SourceFile> productionSources() throws IOException {
        Path root = reactorRoot();
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> normalized(root.relativize(path)).contains("/src/main/java/"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> readSource(root, path))
                    .toList();
        }
    }

    private static SourceFile readSource(Path root, Path path) {
        try {
            return new SourceFile(normalized(root.relativize(path)), Files.readString(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("无法读取生产源码: " + path, exception);
        }
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path reactorRoot() {
        Path candidate = Path.of(System.getProperty("maven.multiModuleProjectDirectory", ""))
                .toAbsolutePath();
        while (candidate != null) {
            Path pom = candidate.resolve("pom.xml");
            try {
                if (Files.isRegularFile(pom)
                        && Files.readString(pom).contains("<artifactId>mom-platform</artifactId>")) {
                    return candidate;
                }
            }
            catch (IOException exception) {
                throw new IllegalStateException("无法定位 Reactor 根目录", exception);
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("找不到 mom-platform Reactor 根目录");
    }

    private record SourceFile(String path, String content) {
        private Set<String> imports() {
            java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
            Matcher matcher = IMPORT.matcher(content);
            while (matcher.find()) {
                values.add(matcher.group(1));
            }
            return Set.copyOf(values);
        }
    }
}
