package io.github.chrisshi.mom.architecture;

import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.webmvc.autoconfigure.I18nWebMvcAutoConfiguration;
import io.github.chrisshi.mom.webmvc.i18n.I18nManagementController;
import io.github.chrisshi.mom.webmvc.i18n.I18nRuntimeController;
import io.github.chrisshi.mom.webmvc.i18n.I18nSseController;
import io.github.chrisshi.mom.webmvc.i18n.LocalI18nChangeNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 锁定 System、Auth、MDM 宿主的 I18n 安全配置和公共 Controller 注册条件。
 * 只加载三个真实 application.yml 的安全/I18n 属性，不启动数据库或分布式服务。
 */
class I18nHostSecurityConfigurationTest {
    private static final String SYSTEM = "mom-system-platform/mom-system-server/src/main/resources/application.yml";
    private static final String AUTH = "mom-auth-platform/mom-auth-server/src/main/resources/application.yml";
    private static final String MDM = "mom-mdm-platform/mom-mdm-server/src/main/resources/application.yml";

    @Test
    void systemHostHasExplicitOwnerAndNoBroadI18nPermit() throws Exception {
        assertHost(SYSTEM, List.of(), "system");
    }

    @Test
    void authHostHasExplicitOwnerAndOnlyLoginConfiguredPublic() throws Exception {
        assertHost(AUTH, List.of("/sessions/login"), "auth");
    }

    @Test
    void mdmHostHasExplicitOwnerAndNoBroadI18nPermit() throws Exception {
        assertHost(MDM, List.of(), "mdm");
    }

    private void assertHost(String relative, List<String> expectedPublic, String owner) throws Exception {
        Path yaml = reactorRoot().resolve(relative);
        PropertySource<?> source = new YamlPropertySourceLoader()
                .load(relative, new FileSystemResource(yaml)).getFirst();
        String enabled = String.valueOf(source.getProperty("mom.security.resource-server.enabled"));
        // System/MDM 允许环境显式覆盖，但正常启动默认必须启用；Auth 必须始终启用。
        assertThat(enabled).as(relative).isIn("true", "${MOM_RESOURCE_SERVER_ENABLED:true}");
        assertThat(source.getProperty("mom.i18n.owner")).isEqualTo(owner);
        assertThat(source.getProperty("mom.i18n.base-locale")).isNull();
        assertThat(source.getProperty("mom.i18n.runtime.base-path")).isNull();
        assertThat(source.getProperty("mom.i18n.management.base-path")).isNull();
        assertThat(source.getProperty("mom.i18n.management.read-authority")).isNull();
        assertThat(source.getProperty("mom.i18n.management.write-authority")).isNull();

        List<String> publicPaths = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> source.getProperty("mom.security.resource-server.public-paths[" + index + "]"))
                .filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        assertThat(publicPaths).as(relative).containsAll(expectedPublic)
                .doesNotContain("/i18n/runtime/**", "/i18n/locales", "/i18n/messages", "/i18n/messages/**",
                        "/api/mdm/**", "/api/system/**", "/admin/**");

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(I18nWebMvcAutoConfiguration.class))
                .withUserConfiguration(HostI18nBeans.class)
                .withPropertyValues("mom.security.resource-server.enabled=true", "mom.i18n.owner=" + owner)
                .run(context -> {
                    assertThat(context).hasSingleBean(I18nRuntimeController.class);
                    assertThat(context).hasSingleBean(I18nSseController.class);
                    assertThat(context).hasSingleBean(I18nManagementController.class);
                });
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

    /** 以宿主真实提供的三种 SPI 类型模拟 Bean 注册前提；不替代宿主集成测试。 */
    @Configuration(proxyBeanMethods = false)
    static class HostI18nBeans {
        @Bean I18nStore store() { return mock(I18nStore.class); }
        @Bean I18nLocalePolicy locales() { return mock(I18nLocalePolicy.class); }
        @Bean LocalI18nChangeNotifier notifier() { return mock(LocalI18nChangeNotifier.class); }
    }
}
