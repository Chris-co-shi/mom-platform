package io.github.chrisshi.mom.core.error;

/**
 * 平台稳定错误码契约。
 *
 * <p>该契约只描述机器可识别错误码与消息解析元数据，不依赖 HTTP、Spring Web、国际化实现或 JSON 序列化。
 * 各模块自行定义具体 ErrorCode，并在各自协议适配层决定 HTTP 状态与国际化解析方式。</p>
 */
public interface ErrorCode {

    /** 稳定机器错误码；调用方逻辑只能依赖该值，不应依赖 message。 */
    String code();

    /** 国际化消息资源 Key；V1 可仅使用默认消息，后续由协议层按 Locale 解析。 */
    String messageKey();

    /** 消息资源缺失时的默认展示文本；V1 默认使用中文。 */
    String defaultMessage();
}
