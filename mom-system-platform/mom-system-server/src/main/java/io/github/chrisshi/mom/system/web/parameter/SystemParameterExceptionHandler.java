package io.github.chrisshi.mom.system.web.parameter;

import io.github.chrisshi.mom.system.application.parameter.SystemParameterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * System Parameter HTTP 的稳定错误映射。
 *
 * <p>响应只公开 code/message，不回显参数值、SQL、堆栈或底层数据库约束名。OAuth2/OIDC 标准错误不经过
 * 此 Advice；认证和授权失败仍由统一 Spring Security Resource Server 返回 401/403。</p>
 */
@RestControllerAdvice(assignableTypes = SystemParameterController.class)
public class SystemParameterExceptionHandler {

    /** 将不存在映射为 404。 */
    @ExceptionHandler(SystemParameterException.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(SystemParameterException.NotFound exception) {
        return error("not_found", exception.getMessage());
    }

    /** 将唯一性或类型一致性冲突映射为 409。 */
    @ExceptionHandler(SystemParameterException.Conflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(SystemParameterException.Conflict exception) {
        return error("conflict", exception.getMessage());
    }

    /** 将乐观锁冲突映射为稳定 409。 */
    @ExceptionHandler(SystemParameterException.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse staleVersion(SystemParameterException.StaleVersion exception) {
        return error("stale_version", exception.getMessage());
    }

    /** 将领域输入和 HTTP 反序列化错误映射为 400。 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "请求参数格式非法";
        return error("invalid_request", message);
    }

    private static ErrorResponse error(String code, String message) {
        return new ErrorResponse(code, message == null ? code : message);
    }

    /** 无成功信封的稳定错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
