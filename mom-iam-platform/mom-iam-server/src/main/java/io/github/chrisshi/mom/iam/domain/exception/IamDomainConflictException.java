package io.github.chrisshi.mom.iam.domain.exception;

/**
 * IAM 领域不变量冲突；不携带数据库、HTTP 或凭证细节。
 */
public class IamDomainConflictException extends RuntimeException {
    public IamDomainConflictException(String message) {
        super(message);
    }
}
