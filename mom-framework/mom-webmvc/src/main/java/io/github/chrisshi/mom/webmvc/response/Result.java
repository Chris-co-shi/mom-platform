package io.github.chrisshi.mom.webmvc.response;

import java.util.Objects;

/**
 * MOM HTTP API 的统一返回信封。
 *
 * <p>该类型只表达 Controller/API 协议，不应作为 Application 的业务返回类型。
 * HTTP 状态码继续表达协议语义，{@code code} 用于稳定的机器错误识别，
 * {@code message} 仅用于展示。</p>
 *
 * @param code 稳定机器码；成功固定为 {@value #SUCCESS_CODE}
 * @param message 展示消息
 * @param data 业务数据；无数据时允许为 {@code null}
 */
public record Result<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "0";
    public static final String SUCCESS_MESSAGE = "success";

    public Result {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static <T> Result<T> failure(String code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> failure(String code, String message, T data) {
        return new Result<>(code, message, data);
    }
}
