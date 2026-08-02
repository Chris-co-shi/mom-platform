package io.github.chrisshi.mom.system.web.dictionary;

import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * System Dictionary HTTP 的脱敏稳定错误映射。
 *
 * <p>响应不回显 SQL、数据库约束、堆栈或内部 Entity。认证与授权错误仍由统一 Resource Server 返回
 * 401/403；该 Advice 只处理 Dictionary Controller 的业务和协议输入错误。</p>
 */
@RestControllerAdvice(assignableTypes = SystemDictionaryController.class)
public class SystemDictionaryExceptionHandler {

    /** 将字典或条目不存在映射为 404。 */
    @ExceptionHandler(SystemDictionaryException.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(SystemDictionaryException.NotFound exception) {
        return error("not_found", exception.getMessage());
    }

    /** 将稳定 Code 唯一冲突映射为 409。 */
    @ExceptionHandler(SystemDictionaryException.Conflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(SystemDictionaryException.Conflict exception) {
        return error("conflict", exception.getMessage());
    }

    /** 将乐观版本冲突映射为稳定 409。 */
    @ExceptionHandler(SystemDictionaryException.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse staleVersion(SystemDictionaryException.StaleVersion exception) {
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

    /** 无成功信封且不暴露基础设施细节的错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
