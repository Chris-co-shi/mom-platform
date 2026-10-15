package io.github.chrisshi.mom.qms.application;

/** QMS Application 向 HTTP 边界暴露的稳定业务失败，不携带数据库细节。 */
public final class QmsException extends RuntimeException {
    public enum Kind { BAD_REQUEST, NOT_FOUND, CONFLICT }
    private final Kind kind;
    private final String code;

    private QmsException(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }
    public Kind kind() { return kind; }
    public String code() { return code; }
    public static QmsException invalidReference() { return new QmsException(Kind.BAD_REQUEST, "qms.invalid_reference"); }
    public static QmsException invalidRange() { return new QmsException(Kind.BAD_REQUEST, "qms.invalid_range"); }
    public static QmsException notFound() { return new QmsException(Kind.NOT_FOUND, "qms.resource_not_found"); }
    public static QmsException conflict() { return new QmsException(Kind.CONFLICT, "qms.code_or_scope_conflict"); }
    public static QmsException versionConflict() { return new QmsException(Kind.CONFLICT, "qms.version_conflict"); }
    public static QmsException immutable() { return new QmsException(Kind.CONFLICT, "qms.immutable_definition"); }
    public static QmsException disabledReference() { return new QmsException(Kind.CONFLICT, "qms.disabled_reference"); }
}
