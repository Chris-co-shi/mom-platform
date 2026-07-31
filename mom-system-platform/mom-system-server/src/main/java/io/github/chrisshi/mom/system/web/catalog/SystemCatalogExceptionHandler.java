package io.github.chrisshi.mom.system.web.catalog;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Catalog HTTP 的稳定两字段脱敏错误映射。 */
@RestControllerAdvice(assignableTypes = {
        SystemCatalogAdminController.class, SystemCatalogRuntimeController.class})
public class SystemCatalogExceptionHandler {
    @ExceptionHandler(SystemCatalogException.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(SystemCatalogException.NotFound exception) {
        return error(exception.code(), exception.getMessage());
    }

    @ExceptionHandler(SystemCatalogException.Conflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(SystemCatalogException.Conflict exception) {
        return error(exception.code(), exception.getMessage());
    }

    @ExceptionHandler(SystemCatalogException.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse stale(SystemCatalogException.StaleVersion exception) {
        return error(exception.code(), exception.getMessage());
    }

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

    public record ErrorResponse(String code, String message) {
    }
}
