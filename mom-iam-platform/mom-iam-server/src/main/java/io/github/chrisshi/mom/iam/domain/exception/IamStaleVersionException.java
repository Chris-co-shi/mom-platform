package io.github.chrisshi.mom.iam.domain.exception;

/** 客户端提交的聚合版本已经过期。 */
public final class IamStaleVersionException extends IamDomainConflictException {
    public IamStaleVersionException(String message) {
        super(message);
    }
}
