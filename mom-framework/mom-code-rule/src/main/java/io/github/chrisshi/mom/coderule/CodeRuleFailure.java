package io.github.chrisshi.mom.coderule;

/**
 * 可映射为 HTTP 状态的规则业务失败。
 */
public final class CodeRuleFailure extends RuntimeException {
    public enum Kind {BAD_REQUEST, NOT_FOUND, CONFLICT}

    private final Kind kind;
    private final String code;

    public CodeRuleFailure(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
