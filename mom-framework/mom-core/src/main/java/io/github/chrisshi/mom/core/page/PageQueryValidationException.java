package io.github.chrisshi.mom.core.page;

/**
 * 平台统一分页请求校验异常。
 *
 * <p>该异常位于 Core 边界，只携带稳定字段名和安全错误信息，不感知 HTTP 状态码或具体数据访问框架。
 * Web 边界可以将其转换为 400，Application 直接调用时也能得到一致的失败语义。异常不可变且线程安全，
 * 抛出时不会产生数据库、网络或消息副作用。</p>
 */
public final class PageQueryValidationException extends IllegalArgumentException {
    private final String field;

    /**
     * 创建分页字段校验异常。
     *
     * @param field 非法字段的稳定 JSON 字段名
     * @param message 可安全返回调用方的中文错误信息
     */
    public PageQueryValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * @return 非法字段的稳定 JSON 字段名
     */
    public String field() {
        return field;
    }
}
