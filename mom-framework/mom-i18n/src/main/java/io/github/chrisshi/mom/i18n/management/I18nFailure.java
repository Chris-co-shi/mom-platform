package io.github.chrisshi.mom.i18n.management;

/**
 * Framework 公共 I18n 用例的稳定失败语义。
 *
 * <p>仅携带可映射 HTTP 状态的稳定 code，不暴露 SQL、技术异常或内部 messageKey。
 * 数据库异常不被此类型吞掉，继续由宿主错误边界按依赖故障处理。</p>
 */
public final class I18nFailure extends RuntimeException {
    /** 管理用例可预期的失败类别。 */
    public enum Kind { NOT_FOUND, CONFLICT }

    private final Kind kind;
    private final String code;

    /** @param kind HTTP 语义类别 @param code 对外稳定错误码 */
    public I18nFailure(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }

    /** @return 失败类别 */
    public Kind kind() {
        return kind;
    }

    /** @return 对外稳定错误码 */
    public String code() {
        return code;
    }
}
