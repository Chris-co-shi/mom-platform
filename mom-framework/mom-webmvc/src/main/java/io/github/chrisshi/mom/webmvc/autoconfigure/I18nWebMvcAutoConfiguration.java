package io.github.chrisshi.mom.webmvc.autoconfigure;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.i18n.ClasspathI18nMessageResolver;
import io.github.chrisshi.mom.webmvc.i18n.I18nChangeNotifier;
import io.github.chrisshi.mom.webmvc.i18n.I18nRuntimeController;
import io.github.chrisshi.mom.webmvc.i18n.I18nRuntimeProvider;
import io.github.chrisshi.mom.webmvc.i18n.I18nSseController;
import io.github.chrisshi.mom.webmvc.i18n.LocalI18nChangeNotifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

/**
 * Servlet 服务通用 I18n Runtime 与 Framework classpath 消息解析自动配置。
 *
 * <p>Resolver 始终可用且不依赖业务服务；只有宿主显式提供 {@link I18nRuntimeProvider} 时才注册 Runtime
 * 与 SSE Controller，避免无 I18n Owner 的服务暴露空端点。所有 Bean 都允许宿主替换。调度器只有一个
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
    @ConditionalOnBean(I18nRuntimeProvider.class)
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
    @ConditionalOnBean(I18nRuntimeProvider.class)
    @ConditionalOnMissingBean(I18nChangeNotifier.class)
    I18nChangeNotifier i18nChangeNotifier(ThreadPoolTaskScheduler scheduler) {
        return new LocalI18nChangeNotifier(scheduler, Duration.ofSeconds(25));
    }

    /** @return 委托宿主 Provider 的通用 Runtime Controller */
    @Bean
    @ConditionalOnBean(I18nRuntimeProvider.class)
    I18nRuntimeController i18nRuntimeController(I18nRuntimeProvider provider) {
        return new I18nRuntimeController(provider);
    }

    /** @return 委托 Framework 通知器的通用 SSE Controller */
    @Bean
    @ConditionalOnBean({I18nRuntimeProvider.class, I18nChangeNotifier.class})
    I18nSseController i18nSseController(I18nChangeNotifier notifier) {
        return new I18nSseController(notifier);
    }
}
