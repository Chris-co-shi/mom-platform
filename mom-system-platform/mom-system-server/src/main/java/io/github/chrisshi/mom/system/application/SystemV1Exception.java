package io.github.chrisshi.mom.system.application;

/**
 * System V1 用例对 Controller 暴露的稳定业务异常基类。
 *
 * <p>异常只携带脱敏机器码和用户可读说明，不包含 SQL、约束名、数据库连接或 Entity。Application 在
 * 数据库唯一冲突、乐观锁冲突、引用不存在和业务规则失败时抛出；事务按 RuntimeException 默认回滚。
 * Controller 负责映射 HTTP 状态，Infrastructure 不依赖本异常。</p>
 */
public class SystemV1Exception extends RuntimeException {
    private final String code;
    private final String messageKey;
    private final Object[] args;

    /**
     * @param code 稳定机器错误码
     * @param messageKey 由数据 Owner 管理的稳定消息键
     * @param args 位置占位符参数，不得包含 Secret
     */
    public SystemV1Exception(String code, String messageKey, Object... args) {
        super(messageKey);
        this.code = code;
        this.messageKey = messageKey;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /** @return 可供 API 客户端稳定分支的机器错误码 */
    public String code() {
        return code;
    }

    /** @return 由 I18nMessageResolver 解析的稳定消息键 */
    public String messageKey() {
        return messageKey;
    }

    /** @return 防御性复制的位置占位符参数 */
    public Object[] args() {
        return args.clone();
    }

    /** 资源不存在。 */
    public static final class NotFound extends SystemV1Exception {
        /** @param messageKey 稳定消息键 */
        public NotFound(String messageKey, Object... args) {
            super("not_found", messageKey, args);
        }
    }

    /** 唯一性、状态或业务不变量冲突。 */
    public static final class Conflict extends SystemV1Exception {
        /** @param code 具体稳定冲突码 */
        public Conflict(String code, String messageKey, Object... args) {
            super(code, messageKey, args);
        }
    }
}
