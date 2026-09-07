package io.github.chrisshi.mom.webmvc.i18n;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 单实例 I18n 变更通知与 SSE 连接注册契约。
 *
 * <p>业务 Application 只调用变更方法，不直接持有 {@link SseEmitter}。Framework 实现负责在当前事务
 * 提交后广播、清理断开的连接和发送心跳。SSE 只是失效通道，不承载 Translation 正文；通知失败不会
 * 回滚已经提交的数据库事实。V1 仅保证单 JVM 实例内广播，不承诺跨实例投递或事件重放。</p>
 */
public interface I18nChangeNotifier {

    /**
     * 注册一个本地 SSE 订阅者。
     *
     * @return 已配置完成回调与超时的 emitter
     */
    SseEmitter subscribe();

    /**
     * 在事务提交后通知指定 Locale/namespace 的 Bundle 已变化。
     *
     * @param localeCode BCP 47 Locale Tag
     * @param namespace 数据 Owner 的 namespace
     */
    void bundleChanged(String localeCode, String namespace);

    /** 在事务提交后通知 SupportedLocale 列表或默认 Locale 已变化。 */
    void localesChanged();

    /**
     * 返回当前进程内活跃订阅数，仅用于测试、健康诊断和资源泄漏观察。
     *
     * @return 当前连接数量
     */
    int activeSubscriberCount();
}
