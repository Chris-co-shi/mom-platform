package io.github.chrisshi.mom.webmvc.autoconfigure;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.i18n.management.I18nManagementService;
import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.resolver.DefaultI18nMessageResolver;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.runtime.I18nChangePublisher;
import io.github.chrisshi.mom.i18n.runtime.I18nRuntimeService;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.webmvc.i18n.ClasspathI18nMessageResolver;
import io.github.chrisshi.mom.webmvc.i18n.I18nChangeNotifier;
import io.github.chrisshi.mom.webmvc.i18n.I18nManagementAccess;
import io.github.chrisshi.mom.webmvc.i18n.I18nManagementController;
import io.github.chrisshi.mom.webmvc.i18n.I18nManagementExceptionHandler;
import io.github.chrisshi.mom.webmvc.i18n.I18nRuntimeController;
import io.github.chrisshi.mom.webmvc.i18n.I18nSseController;
import io.github.chrisshi.mom.webmvc.i18n.LocalI18nChangeNotifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

/**
 * Servlet 服务通用 I18n Runtime 与 Framework classpath 消息解析自动配置。
 *
 * <p>Framework classpath Resolver 始终可用；只有宿主显式提供 {@link I18nStore}、namespace 和 Locale
 * 策略时才注册 Runtime、Management 与 SSE。所有 Bean 都允许宿主替换。调度器只有一个
 * daemon 线程，关闭 ApplicationContext 时释放；通知失败不影响数据库事务。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class I18nWebMvcAutoConfiguration {

    /** @return 默认的 Framework classpath 消息解析器 */
    @Bean
    @ConditionalOnMissingBean(I18nMessageResolver.class)
    I18nMessageResolver frameworkI18nMessageResolver() {
        return new ClasspathI18nMessageResolver();
    }

    /** @return 仅服务 I18n SSE 心跳的单线程 daemon 调度器 */
    @Bean(name = "i18nHeartbeatTaskScheduler")
    @ConditionalOnBean(I18nStore.class)
    ThreadPoolTaskScheduler i18nHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("i18n-sse-heartbeat-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    /**
     * @param scheduler I18n 专用生命周期调度器
     * @return 单实例 after-commit SSE 通知器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(I18nStore.class)
    @ConditionalOnMissingBean(I18nChangeNotifier.class)
    LocalI18nChangeNotifier i18nChangeNotifier(ThreadPoolTaskScheduler scheduler) {
        return new LocalI18nChangeNotifier(scheduler, Duration.ofSeconds(25));
    }

    /** @return 复用宿主 Store 的公共批量 Runtime 用例 */
    @Bean
    @ConditionalOnBean({I18nStore.class, I18nNamespacePolicy.class, I18nLocalePolicy.class})
    I18nRuntimeService i18nRuntimeService(I18nStore store, I18nNamespacePolicy namespaces,
                                          I18nLocalePolicy locales) {
        return new I18nRuntimeService(store, namespaces, locales);
    }

    /** @return 单条业务 Error 使用的精确查询 Resolver，Framework 技术错误仍走 classpath */
    @Bean
    @Primary
    @ConditionalOnBean({I18nStore.class, I18nNamespacePolicy.class, I18nLocalePolicy.class})
    I18nMessageResolver ownerI18nMessageResolver(I18nStore store, I18nNamespacePolicy namespaces,
                                                  I18nLocalePolicy locales) {
        return new DefaultI18nMessageResolver(store, namespaces, locales,
                new ClasspathI18nMessageResolver());
    }

    /** @return Framework 公共事务管理用例 */
    @Bean
    @ConditionalOnBean({I18nStore.class, I18nNamespacePolicy.class, I18nLocalePolicy.class,
            I18nChangePublisher.class})
    I18nManagementService i18nManagementService(I18nStore store, I18nNamespacePolicy namespaces,
                                                 I18nLocalePolicy locales, I18nChangePublisher notifier) {
        return new I18nManagementService(store, namespaces, locales, notifier);
    }

    /** @return 宿主明确配置的管理权限；未配置时不会意外授予权限 */
    @Bean("i18nManagementAccess")
    @ConditionalOnBean(I18nStore.class)
    I18nManagementAccess i18nManagementAccess(
            @Value("${mom.i18n.management.read-authority:__I18N_UNCONFIGURED_READ__}") String read,
            @Value("${mom.i18n.management.write-authority:__I18N_UNCONFIGURED_WRITE__}") String write) {
        return new I18nManagementAccess(read, write);
    }

    /** @return 公共匿名 Runtime Controller */
    @Bean
    @ConditionalOnBean(I18nRuntimeService.class)
    I18nRuntimeController i18nRuntimeController(I18nRuntimeService runtime) {
        return new I18nRuntimeController(runtime);
    }

    /** @return 公共受保护 Management Controller */
    @Bean
    @ConditionalOnBean({I18nManagementService.class, I18nManagementAccess.class})
    @ConditionalOnProperty(prefix = "mom.security.resource-server", name = "enabled", havingValue = "true")
    I18nManagementController i18nManagementController(I18nManagementService management) {
        return new I18nManagementController(management);
    }

    /** @return 公共 I18n HTTP 失败映射 */
    @Bean
    @ConditionalOnBean(I18nStore.class)
    I18nManagementExceptionHandler i18nManagementExceptionHandler(I18nMessageResolver messages) {
        return new I18nManagementExceptionHandler(messages);
    }

    /** @return 委托 Framework 通知器的通用 SSE Controller */
    @Bean
    @ConditionalOnBean({I18nRuntimeService.class, I18nChangeNotifier.class})
    I18nSseController i18nSseController(I18nChangeNotifier notifier) {
        return new I18nSseController(notifier);
    }
}
