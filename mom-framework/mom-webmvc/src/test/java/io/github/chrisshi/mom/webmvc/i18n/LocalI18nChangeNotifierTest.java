package io.github.chrisshi.mom.webmvc.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单实例 SSE 注册表生命周期与最小事件负载测试。
 */
class LocalI18nChangeNotifierTest {

    /** 关闭通知器必须释放全部订阅，事件结构不得出现 Translation 正文字段。 */
    @Test
    void shouldReleaseSubscribersAndExposeOnlyInvalidationFields() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setDaemon(true);
        scheduler.initialize();
        LocalI18nChangeNotifier notifier = new LocalI18nChangeNotifier(scheduler, Duration.ofHours(1));
        try {
            notifier.subscribe();
            assertThat(notifier.activeSubscriberCount()).isEqualTo(1);
            assertThat(Arrays.stream(LocalI18nChangeNotifier.I18nChangeEvent.class.getRecordComponents())
                    .map(component -> component.getName()))
                    .containsExactly("type", "locale", "namespace")
                    .doesNotContain("messageText", "translation", "messages");
        } finally {
            notifier.close();
            scheduler.shutdown();
        }
        assertThat(notifier.activeSubscriberCount()).isZero();
    }
}
