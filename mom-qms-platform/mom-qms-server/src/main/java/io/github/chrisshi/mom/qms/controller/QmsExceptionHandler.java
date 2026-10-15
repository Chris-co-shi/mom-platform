package io.github.chrisshi.mom.qms.controller;

import feign.FeignException;
import feign.RetryableException;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.qms.application.QmsException;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Locale;

/** QMS 业务失败和 MDM 同步调用失败的安全 HTTP 映射。 */
@RestControllerAdvice(basePackages = "io.github.chrisshi.mom.qms.controller")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class QmsExceptionHandler {
    private final I18nMessageResolver messages;
    public QmsExceptionHandler(I18nMessageResolver messages) { this.messages = messages; }
    @ExceptionHandler(QmsException.class)
    public ResponseEntity<Result<Void>> handle(QmsException exception, Locale locale) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST; case NOT_FOUND -> HttpStatus.NOT_FOUND; case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(Result.failure(exception.code(),
                messages.resolve(exception.code(), locale)));
    }
    /** 保留 MDM 401/403/404/409/503/504 协议语义；响应体不泄漏远端实现细节。 */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Result<Void>> handleFeign(FeignException exception, Locale locale) {
        int raw = exception instanceof RetryableException ? 503 : exception.status();
        HttpStatus status = switch (raw) {
            case 401 -> HttpStatus.UNAUTHORIZED; case 403 -> HttpStatus.FORBIDDEN; case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT; case 504 -> HttpStatus.GATEWAY_TIMEOUT; default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(Result.failure("qms.mdm_reference_unavailable",
                messages.resolve("qms.mdm_reference_unavailable", locale)));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, PageQueryValidationException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception exception, Locale locale) {
        return ResponseEntity.badRequest().body(Result.failure("request.validation_failed",
                messages.resolve("framework.validation.failed", locale)));
    }
    /** JSON 类型错误及被 Jackson 包装的分页不变量均返回稳定 400。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException exception, Locale locale) {
        String code = hasPageFailure(exception) ? "request.pagination_invalid" : "request.invalid_body";
        return ResponseEntity.badRequest().body(Result.failure(code,
                messages.resolve("framework.web.invalid_request", locale)));
    }
    private static boolean hasPageFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PageQueryValidationException) return true;
            current = current.getCause();
        }
        return false;
    }
}
