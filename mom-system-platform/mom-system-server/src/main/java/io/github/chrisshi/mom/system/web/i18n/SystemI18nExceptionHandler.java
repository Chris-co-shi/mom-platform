package io.github.chrisshi.mom.system.web.i18n;

import io.github.chrisshi.mom.system.application.i18n.SystemI18nException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Dynamic I18n HTTP 的稳定脱敏错误映射。
 *
 * <p>只处理 I18n Controller 的业务/输入错误；认证授权仍由 Resource Server 返回 401/403。响应不包含
 * SQL、约束名、堆栈、消息正文或内部 Entity，数据库基础设施异常也不会转换为成功。</p>
 */
@RestControllerAdvice(assignableTypes = SystemI18nController.class)
public class SystemI18nExceptionHandler {
    /** 资源、草稿、发布版本不存在或不完整时返回 404。 */
    @ExceptionHandler(SystemI18nException.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(SystemI18nException.NotFound exception) {
        return error("not_found", exception.getMessage());
    }

    /** 唯一、No-op 或状态冲突返回 409。 */
    @ExceptionHandler(SystemI18nException.Conflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(SystemI18nException.Conflict exception) {
        return error("conflict", exception.getMessage());
    }

    /** 乐观版本冲突返回稳定 409。 */
    @ExceptionHandler(SystemI18nException.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse stale(SystemI18nException.StaleVersion exception) {
        return error("stale_version", exception.getMessage());
    }

    /** 领域输入和 HTTP 反序列化错误返回 400。 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage() : "请求参数格式非法";
        return error("invalid_request", message);
    }

    private static ErrorResponse error(String code, String message) {
        return new ErrorResponse(code, message == null ? code : message);
    }

    /** 无成功信封且不暴露基础设施细节的错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
