package io.github.chrisshi.mom.auth.infrastructure.configuration;

import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import io.github.chrisshi.mom.auth.controller.response.FieldErrorResponse;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

/**
 * Mini Auth HTTP 异常适配器。
 *
 * <p>异常继续使用真实 HTTP 状态码，同时响应体统一为 {@link Result}。
 * V1 仅使用 ErrorCode.defaultMessage，不启用 MessageSource/Locale 转换。</p>
 */
@RestControllerAdvice(basePackages = "io.github.chrisshi.mom.auth.controller")
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<Result<Void>> handleAuthException(AuthException exception) {
        HttpStatus status = statusOf(exception.errorCode());
        return ResponseEntity.status(status).body(
            Result.failure(exception.errorCode().code(), exception.getMessage())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Result<List<FieldErrorResponse>>> handleBodyValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(AuthExceptionHandler::toFieldError)
            .toList();
        return ResponseEntity.badRequest().body(
            Result.failure("request.validation_failed", "请求参数校验失败", fieldErrors)
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Result<List<FieldErrorResponse>>> handleMethodValidation(HandlerMethodValidationException exception) {
        List<FieldErrorResponse> fieldErrors = exception.getAllErrors().stream()
            .map(AuthExceptionHandler::toMethodFieldError)
            .toList();
        return ResponseEntity.badRequest().body(
            Result.failure("request.validation_failed", "请求参数校验失败", fieldErrors)
        );
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
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USERNAME_CONFLICT, ROLE_CODE_CONFLICT, PERMISSION_CODE_CONFLICT,
                 RESOURCE_REFERENCED, OPTIMISTIC_LOCK_CONFLICT -> HttpStatus.CONFLICT;
            case AUTHENTICATION_SERVICE_UNAVAILABLE, TOKEN_STORE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
