package io.github.chrisshi.mom.auth.application;

public final class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public AuthException(AuthErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public AuthException(AuthErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public AuthException(AuthErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }
}
