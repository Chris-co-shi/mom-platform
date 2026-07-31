package io.github.chrisshi.mom.iam.application.admin;

/** IAM Admin 稳定应用异常；不携带 SQL、请求正文或凭证。 */
public final class IamAdminExceptions {
    private IamAdminExceptions() { }

    public static final class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static final class StaleVersion extends RuntimeException {
        public StaleVersion(String message) { super(message); }
    }

    public static final class DependencyUnavailable extends RuntimeException {
        public DependencyUnavailable(String message) { super(message); }
    }

    public static final class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }
}
