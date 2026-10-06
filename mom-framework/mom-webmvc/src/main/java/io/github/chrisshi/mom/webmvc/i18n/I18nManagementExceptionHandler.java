package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.i18n.management.I18nFailure;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Locale;

/**
 * 公共 I18n HTTP 入口的安全失败映射。
 *
 * <p>响应只含稳定 code 与最终 message，不泄漏内部 messageKey、SQL 或堆栈。
 * 认证授权异常仍交给 Resource Server Filter Chain；数据库故障返回 503，不伪造空 Bundle。</p>
 */
@RestControllerAdvice(assignableTypes = {I18nManagementController.class, I18nRuntimeController.class})
public final class I18nManagementExceptionHandler {
    private final I18nMessageResolver messages;

    /** @param messages 宿主的业务与 Framework classpath 解析器 */
    public I18nManagementExceptionHandler(I18nMessageResolver messages) {
        this.messages = messages;
    }

    /** @return 不存在资源的 404 或业务冲突的 409 */
    @ExceptionHandler(I18nFailure.class)
    public ResponseEntity<Result<Void>> business(I18nFailure failure, Locale locale) {
        HttpStatus status = failure.kind() == I18nFailure.Kind.NOT_FOUND
                ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        String message = messages.resolve("framework.i18n." + failure.code().substring("i18n.".length()), locale);
        return ResponseEntity.status(status).body(Result.failure(failure.code(), message));
    }

    /** @return 非法请求的 400，不回显输入原文 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> invalid(Exception exception, Locale locale) {
        return ResponseEntity.badRequest().body(Result.failure("invalid_request",
                messages.resolve("framework.web.invalid_request", locale)));
    }

    /** @return 本服务数据库不可用时的 503，不返回空成功 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> unavailable(DataAccessException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.failure("dependency_unavailable",
                        messages.resolve("framework.error.internal", locale)));
    }
}
