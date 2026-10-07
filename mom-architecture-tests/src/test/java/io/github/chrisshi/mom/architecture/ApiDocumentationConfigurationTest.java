package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定三个宿主的文档开关与本机安全边界。
 *
 * <p>只读取实际 YAML，不启动数据库或网络；运行时生成与匿名访问由本地文档探针单独验证。
 * 测试无共享可变状态和事务副作用，配置漂移时直接失败。</p>
 */
class ApiDocumentationConfigurationTest {
    private static final List<String> HOSTS = List.of(
            "mom-auth-platform/mom-auth-server",
            "mom-system-platform/mom-system-server",
            "mom-mdm-platform/mom-mdm-server");

    /**
     * Base 必须关闭文档，local 才能在 loopback 匿名查看，且不放开普通业务路径。
     *
     * @throws Exception 配置文件缺失或格式无效时让测试失败
     */
    @Test
    void documentationMustBeLocalOnlyWithoutWeakeningBusinessSecurity() throws Exception {
        for (String host : HOSTS) {
            PropertySource<?> base = source(host + "/src/main/resources/application.yml");
            PropertySource<?> local = source(host + "/src/main/resources/application-local.yml");
            assertThat(base.getProperty("springdoc.api-docs.enabled")).as(host).isEqualTo(false);
            assertThat(base.getProperty("springdoc.swagger-ui.enabled")).as(host).isEqualTo(false);
            assertThat(publicPaths(base)).as(host).noneMatch(path -> path.contains("api-docs") || path.contains("swagger"));
            assertThat(local.getProperty("server.address")).as(host).isEqualTo("127.0.0.1");
            assertThat(local.getProperty("springdoc.api-docs.enabled")).as(host).isEqualTo(true);
            assertThat(local.getProperty("springdoc.swagger-ui.enabled")).as(host).isEqualTo(true);
            assertThat(publicPaths(local)).as(host).contains("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .doesNotContain("/**", "/api/**", "/admin/**");
            assertThat(publicPaths(local)).as(host).containsAll(publicPaths(base));
        }
    }

    private static PropertySource<?> source(String relative) throws Exception {
        return new YamlPropertySourceLoader().load(relative,
                new FileSystemResource(reactorRoot().resolve(relative))).getFirst();
    }

    private static List<String> publicPaths(PropertySource<?> source) {
        return IntStream.range(0, 30)
                .mapToObj(index -> source.getProperty("mom.security.resource-server.public-paths[" + index + "]"))
                .filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private static Path reactorRoot() {
        Path candidate = Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml")) &&
                    Files.isDirectory(candidate.resolve("mom-architecture-tests"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("无法定位 MOM Reactor 根目录");
    }
}
