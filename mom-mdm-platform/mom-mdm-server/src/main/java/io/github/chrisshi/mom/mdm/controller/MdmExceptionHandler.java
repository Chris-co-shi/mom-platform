package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;
import java.util.Locale;

/**
 * MDM Controller 的 HTTP 异常适配器。
 *
 * <p>该类只负责将 Application 的稳定异常与 Bean Validation 映射为 Result 和真实 HTTP 状态，不暴露 SQL、
 * 约束名或堆栈；数据库不可用等未预期异常继续交给平台统一 500/503 处理。</p>
 */
@RestControllerAdvice(basePackages = "io.github.chrisshi.mom.mdm.controller")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MdmExceptionHandler {
    private final I18nMessageResolver messages;

    /** @param messages MDM 自有错误与 Framework 技术错误解析器 */
    public MdmExceptionHandler(I18nMessageResolver messages) {
        this.messages = messages;
    }

    /** 将 MDM 业务异常转换为 400、404 或 409。 */
    @ExceptionHandler(MdmException.class)
    public ResponseEntity<Result<Void>> handleMdmException(MdmException exception, Locale locale) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(Result.failure(exception.code(),
                messages.resolve(exception.namespace(), exception.messageKey(), locale, exception.args())));
    }

    /** 将请求体字段校验错误转换为稳定的 400 响应，不产生业务写副作用。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<FieldErrorView>>> handleValidation(MethodArgumentNotValidException exception,
                                                                           Locale locale) {
        List<FieldErrorView> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(MdmExceptionHandler::toFieldError).toList();
        return ResponseEntity.badRequest().body(Result.failure(
                "request.validation_failed", messages.resolve("framework.validation.failed", locale), errors));
    }

    /** 将分页等方法参数校验错误转换为稳定的 400 响应。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<List<FieldErrorView>>> handleMethodValidation(
            HandlerMethodValidationException exception, Locale locale) {
        List<FieldErrorView> errors = exception.getAllErrors().stream()
                .map(MdmExceptionHandler::toMethodError).toList();
        return ResponseEntity.badRequest().body(Result.failure(
                "request.validation_failed", messages.resolve("framework.validation.failed", locale), errors));
    }

    /** 将 Application 中配置化分页校验失败转换为稳定的 400 响应。 */
    @ExceptionHandler(PageQueryValidationException.class)
    public ResponseEntity<Result<List<FieldErrorView>>> handlePageQueryValidation(
            PageQueryValidationException exception, Locale locale) {
        return paginationFailure(exception, locale);
    }

    /**
     * 将分页记录构造失败或其他不可读 JSON 转换为安全的 400 响应。
     *
     * <p>Jackson 会包装 PageQuery 紧凑构造器抛出的异常，因此沿 Cause 链识别平台分页异常；其他 JSON
     * 语法或类型错误只返回通用消息，不暴露反序列化实现细节。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<? extends Result<?>> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                                       Locale locale) {
        PageQueryValidationException pageException = findPageQueryValidation(exception);
        if (pageException != null) {
            return paginationFailure(pageException, locale);
        }
        return ResponseEntity.badRequest().body(Result.failure(
                "request.invalid_body", messages.resolve("framework.web.invalid_request", locale)));
    }

    private ResponseEntity<Result<List<FieldErrorView>>> paginationFailure(
            PageQueryValidationException exception, Locale locale) {
        List<FieldErrorView> errors = List.of(new FieldErrorView(
                exception.field(), "invalid", exception.getMessage()));
        return ResponseEntity.badRequest().body(Result.failure(
                "request.pagination_invalid", messages.resolve("framework.web.invalid_request", locale), errors));
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

    private static FieldErrorView toFieldError(FieldError error) {
        return new FieldErrorView(error.getField(), error.getCode() == null ? "invalid" : error.getCode(),
                error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage());
    }

    private static FieldErrorView toMethodError(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String code = codes == null || codes.length == 0 ? "invalid" : codes[0];
        return new FieldErrorView("request", code,
                error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage());
    }

    /** 脱敏的字段错误响应。 */
    public record FieldErrorView(String field, String code, String message) { }
}
