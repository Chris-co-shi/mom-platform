package io.github.chrisshi.mom.core.security;

/**
 * 数据写入需要审计操作人，但当前既无已认证用户也无显式系统上下文时抛出的异常。
 *
 * <p>异常不携带 Token、Authorization Header 或完整认证对象，避免在日志和错误链中泄漏敏感凭证。</p>
 */
public final class AuditActorMissingException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private static final String MESSAGE =
            "数据库写入需要明确的审计操作人；请提供已认证用户或显式 SYSTEM Actor";

    /** 创建缺少操作人的安全异常。 */
    public AuditActorMissingException() {
        super(MESSAGE);
    }
}
