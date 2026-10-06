package io.github.chrisshi.mom.auth.infrastructure.configuration;

import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import io.github.chrisshi.mom.auth.controller.response.FieldErrorResponse;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;
import java.util.Locale;

/**
 * Mini Auth HTTP 异常适配器。
 *
 * <p>异常继续使用真实 HTTP 状态码，同时响应体统一为 {@link Result}。
 * 业务 ErrorCode 仅保留稳定 Key；HTTP 边界按请求 Locale 查询 Auth 自有译文。
 * 数据库不可用时解析失败向上传播，不回退为单一中文。</p>
 */
@RestControllerAdvice(basePackages = "io.github.chrisshi.mom.auth.controller")
public class AuthExceptionHandler {
    private final I18nMessageResolver messages;

    /** @param messages Auth 业务与 Framework 技术错误共用的解析边界 */
    public AuthExceptionHandler(I18nMessageResolver messages) {
        this.messages = messages;
    }

    /** 根据请求 Locale 解析业务错误，不改机器错误码。 */
    @ExceptionHandler(AuthException.class)
    ResponseEntity<Result<Void>> handleAuthException(AuthException exception, Locale locale) {
        HttpStatus status = statusOf(exception.errorCode());
        return ResponseEntity.status(status).body(
            Result.failure(exception.errorCode().code(),
                    messages.resolve(exception.errorCode().namespace(), exception.errorCode().localMessageKey(), locale))
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Result<List<FieldErrorResponse>>> handleBodyValidation(MethodArgumentNotValidException exception,
                                                                            Locale locale) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(AuthExceptionHandler::toFieldError)
            .toList();
        return ResponseEntity.badRequest().body(
            Result.failure("request.validation_failed", messages.resolve("framework.validation.failed", locale), fieldErrors)
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Result<List<FieldErrorResponse>>> handleMethodValidation(HandlerMethodValidationException exception,
                                                                              Locale locale) {
        List<FieldErrorResponse> fieldErrors = exception.getAllErrors().stream()
            .map(AuthExceptionHandler::toMethodFieldError)
            .toList();
        return ResponseEntity.badRequest().body(
            Result.failure("request.validation_failed", messages.resolve("framework.validation.failed", locale), fieldErrors)
        );
    }

    /** 将配置化分页校验失败转换为稳定的 400 响应。 */
    @ExceptionHandler(PageQueryValidationException.class)
    ResponseEntity<Result<List<FieldErrorResponse>>> handlePageQueryValidation(
        PageQueryValidationException exception, Locale locale
    ) {
        return paginationFailure(exception, locale);
    }

    /**
     * 将分页记录构造失败或其他不可读 JSON 转换为安全的 400 响应。
     *
     * <p>分页紧凑构造器异常会被 Jackson 包装，因此沿 Cause 链识别；其他解析错误不回显内部类型。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<? extends Result<?>> handleUnreadableBody(HttpMessageNotReadableException exception, Locale locale) {
        PageQueryValidationException pageException = findPageQueryValidation(exception);
        if (pageException != null) {
            return paginationFailure(pageException, locale);
        }
        return ResponseEntity.badRequest().body(
            Result.failure("request.invalid_body", messages.resolve("framework.web.invalid_request", locale))
        );
    }

    private ResponseEntity<Result<List<FieldErrorResponse>>> paginationFailure(
        PageQueryValidationException exception, Locale locale
    ) {
        List<FieldErrorResponse> errors = List.of(
            new FieldErrorResponse(exception.field(), "invalid", exception.getMessage())
        );
        return ResponseEntity.badRequest().body(
            Result.failure("request.pagination_invalid", messages.resolve("framework.web.invalid_request", locale), errors)
        );
    }

    private static PageQueryValidationException findPageQueryValidation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PageQueryValidationException pageException) {
                return pageException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static FieldErrorResponse toFieldError(FieldError error) {
        return new FieldErrorResponse(
            error.getField(),
            error.getCode() == null ? "invalid" : error.getCode(),
            error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage()
        );
    }

    private static FieldErrorResponse toMethodFieldError(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String code = codes == null || codes.length == 0 ? "invalid" : codes[0];
        String message = error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage();
        return new FieldErrorResponse("request", code, message);
    }

    private static HttpStatus statusOf(AuthErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case ACCOUNT_DISABLED -> HttpStatus.FORBIDDEN;
            case RELATION_SELECTION_TOO_LARGE, CURRENT_PASSWORD_INVALID, RESOURCE_INVALID_CODE -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USERNAME_CONFLICT, ROLE_CODE_CONFLICT, PERMISSION_CODE_CONFLICT, RESOURCE_CODE_CONFLICT, RESOURCE_DISABLED,
                 ROLE_DISABLED, PERMISSION_DISABLED, RESOURCE_REFERENCED,
                 OPTIMISTIC_LOCK_CONFLICT -> HttpStatus.CONFLICT;
            case AUTHENTICATION_SERVICE_UNAVAILABLE, TOKEN_STORE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
