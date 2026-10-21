package io.github.chrisshi.mom.webmvc.coderule;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Locale;

/** 编码规则公共接口的安全错误映射。 */
@RestControllerAdvice(assignableTypes = {CodeRuleController.class, CodeGenerationController.class})
@RequiredArgsConstructor
public final class CodeRuleExceptionHandler {
    private final I18nMessageResolver messages;

    @ExceptionHandler(CodeRuleFailure.class)
    public ResponseEntity<Result<Void>> business(CodeRuleFailure failure, Locale locale) {
        HttpStatus status = switch (failure.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        String key = switch (failure.kind()) {
            case BAD_REQUEST -> "framework.web.invalid_request";
            case NOT_FOUND -> "framework.error.not_found";
            case CONFLICT -> "framework.error.conflict";
        };
        return ResponseEntity.status(status).body(Result.failure("code_rule." + failure.code(), messages.resolve(key, locale)));
    }
    @ExceptionHandler(CodeSegmentException.class)
    public ResponseEntity<Result<Void>> invalid(CodeSegmentException ex, Locale locale) {
        return ResponseEntity.badRequest().body(Result.failure("code_rule.invalid_catalog",
                messages.resolve("framework.web.invalid_request", locale)));
    }
    @ExceptionHandler({PageQueryValidationException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> invalidRequest(Exception ex, Locale locale) {
        return ResponseEntity.badRequest().body(Result.failure("code_rule.invalid_request",
                messages.resolve("framework.web.invalid_request", locale)));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> unavailable(DataAccessException ex, Locale locale) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.failure("dependency_unavailable",
                messages.resolve("framework.error.internal", locale)));
    }
}
