package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import io.github.chrisshi.mom.auth.controller.response.FieldErrorResponse;
import io.github.chrisshi.mom.core.context.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.List;

@RestControllerAdvice(basePackages = "io.github.chrisshi.mom.auth.controller")
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ProblemDetail> handleAuthException(AuthException exception, HttpServletRequest request) {
        HttpStatus status = statusOf(exception.errorCode());
        ProblemDetail problem = problem(status, exception.getMessage(), exception.errorCode().code(), request);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(AuthExceptionHandler::toFieldError)
            .toList();
        ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            "请求参数校验失败",
            "request.validation_failed",
            request
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(
        HandlerMethodValidationException exception,
        HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getAllErrors().stream()
            .map(AuthExceptionHandler::toMethodFieldError)
            .toList();
        ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            "请求参数校验失败",
            "request.validation_failed",
            request
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://mom.example/problems/" + code.replace('.', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        String correlationId = CorrelationContext.currentId();
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
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
            case TOKEN_STORE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
