package io.github.chrisshi.mom.webmvc.i18n;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 基于进程内并发注册表实现的 SSE 变更通知器。
 *
 * <p>连接表使用 {@link ConcurrentHashMap}，广播允许并发订阅和断开；失败连接立即完成并移除。通知调用
 * 发生在活动 Spring 事务中时注册 {@code afterCommit} 回调，回滚不发送事件。心跳只发送 SSE comment，
 * 不包含业务文案。进程重启会丢失连接与通知历史，这是 V1 单实例、无重放语义的明确取舍。</p>
 */
public final class LocalI18nChangeNotifier implements I18nChangeNotifier, AutoCloseable {
    private static final long EMITTER_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final Map<String, SseEmitter> subscribers = new ConcurrentHashMap<>();
    private final ScheduledFuture<?> heartbeatTask;

    /**
     * 创建本地通知器并启动固定间隔心跳。
     *
     * @param taskScheduler Framework 管理生命周期的调度器
     * @param heartbeatInterval 心跳间隔，必须为正数
     */
    public LocalI18nChangeNotifier(TaskScheduler taskScheduler, Duration heartbeatInterval) {
        if (heartbeatInterval == null || heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("heartbeatInterval 必须大于 0");
        }
        this.heartbeatTask = taskScheduler.scheduleWithFixedDelay(this::heartbeat, heartbeatInterval);
    }

    /** {@inheritDoc} */
    @Override
    public SseEmitter subscribe() {
        String subscriberId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        subscribers.put(subscriberId, emitter);
        Runnable cleanup = () -> subscribers.remove(subscriberId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        send(subscriberId, emitter, SseEmitter.event().name("I18N_CONNECTED")
                .data(new I18nChangeEvent("I18N_CONNECTED", null, null)));
        return emitter;
    }

    /** {@inheritDoc} */
    @Override
    public void bundleChanged(String localeCode, String namespace) {
        if (localeCode == null || localeCode.isBlank() || namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("localeCode 和 namespace 不能为空");
        }
        publishAfterCommit(new I18nChangeEvent("I18N_BUNDLE_CHANGED", localeCode, namespace));
    }

    /** {@inheritDoc} */
    @Override
    public void localesChanged() {
        publishAfterCommit(new I18nChangeEvent("I18N_LOCALES_CHANGED", null, null));
    }

    /** {@inheritDoc} */
    @Override
    public int activeSubscriberCount() {
        return subscribers.size();
    }

    /**
     * 停止心跳并完成全部本地连接；不会影响业务数据。
     */
    @Override
    public void close() {
        heartbeatTask.cancel(false);
        subscribers.forEach((id, emitter) -> emitter.complete());
        subscribers.clear();
    }

    private void publishAfterCommit(I18nChangeEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(event);
                }
            });
            return;
        }
        broadcast(event);
    }

    private void broadcast(I18nChangeEvent event) {
        subscribers.forEach((id, emitter) -> send(id, emitter,
                SseEmitter.event().name(event.type()).data(event)));
    }

    private void heartbeat() {
        subscribers.forEach((id, emitter) -> send(id, emitter,
                SseEmitter.event().comment("i18n-heartbeat")));
    }

    private void send(String subscriberId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            subscribers.remove(subscriberId, emitter);
            emitter.complete();
        }
    }

    /**
     * SSE 失效事件；只包含定位 Bundle 所需字段，不包含 Translation 正文。
     *
     * @param type 稳定事件类型
     * @param locale Locale 变化事件可为空
     * @param namespace Locale 列表变化事件可为空
     */
    public record I18nChangeEvent(String type, String locale, String namespace) {
    }
}
