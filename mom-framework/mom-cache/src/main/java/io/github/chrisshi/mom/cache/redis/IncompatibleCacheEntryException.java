package io.github.chrisshi.mom.cache.redis;

/**
 * 表示缓存信封版本或逻辑类型与当前 Region 不兼容。
 *
 * <p>不兼容数据不会尝试迁移为 Object/Map，也不会返回旧值；Redis Adapter 会删除当前精确 Key 并按 Miss
 * 回源。异常本身不携带 Payload，避免错误日志泄露缓存内容。</p>
 */
public class IncompatibleCacheEntryException extends RuntimeException {

    /**
     * 创建不兼容异常。
     *
     * @param message 仅描述版本或类型差异的非敏感信息
     */
    public IncompatibleCacheEntryException(String message) {
        super(message);
    }
}
