package io.github.chrisshi.mom.cache.redis;

/**
 * 表示缓存信封或 Payload 无法按声明格式序列化/反序列化。
 *
 * <p>该异常只在 Cache Adapter 内部传播，Redis Provider 捕获后记录低基数错误指标、删除精确损坏 Key 并
 * 返回 Miss。异常消息不得包含 Payload、Token 或敏感业务字段。</p>
 */
public class CacheSerializationException extends RuntimeException {

    /**
     * 包装底层 JSON 处理失败。
     *
     * @param message 不包含 Payload 的稳定错误说明
     * @param cause Jackson 原始异常
     */
    public CacheSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
