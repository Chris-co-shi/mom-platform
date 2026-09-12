package io.github.chrisshi.mom.webmvc.i18n;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 复用 Framework 本地连接注册表的 I18n SSE Controller。
 *
 * <p>该入口只建立当前 JVM 的失效通知连接，不读取或推送 Translation 正文。客户端断线后继续使用最后
 * Bundle，重连后应主动重新加载已使用的 namespace；V1 不提供 Last-Event-ID、历史重放或跨实例广播。</p>
 */
@RestController
@RequestMapping("${mom.i18n.runtime.base-path:/i18n/runtime}")
public final class I18nSseController {
    private final I18nChangeNotifier notifier;

    /** @param notifier Framework 管理的单实例通知器 */
    public I18nSseController(I18nChangeNotifier notifier) {
        this.notifier = notifier;
    }

    /**
     * 建立 SSE 连接。
     *
     * @return 带超时、断线清理和心跳的 emitter
     */
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return notifier.subscribe();
    }
}
