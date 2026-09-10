package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

/**
 * MDM Controller 的 HTTP 异常适配器。
 *
 * <p>该类只负责将 Application 的稳定异常与 Bean Validation 映射为 Result 和真实 HTTP 状态，不暴露 SQL、
 * 约束名或堆栈；数据库不可用等未预期异常继续交给平台统一 500/503 处理。</p>
 */
@RestControllerAdvice(basePackages = "io.github.chrisshi.mom.mdm.controller")
public class MdmExceptionHandler {

    /** 将 MDM 业务异常转换为 400、404 或 409。 */
    @ExceptionHandler(MdmException.class)
    public ResponseEntity<Result<Void>> handleMdmException(MdmException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(Result.failure(exception.code(), exception.getMessage()));
    }

    /** 将请求体字段校验错误转换为稳定的 400 响应，不产生业务写副作用。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<List<FieldErrorView>>> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorView> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(MdmExceptionHandler::toFieldError).toList();
        return ResponseEntity.badRequest().body(Result.failure(
                "request.validation_failed", "请求参数校验失败", errors));
    }

    /** 将分页等方法参数校验错误转换为稳定的 400 响应。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<List<FieldErrorView>>> handleMethodValidation(
            HandlerMethodValidationException exception) {
        List<FieldErrorView> errors = exception.getAllErrors().stream()
                .map(MdmExceptionHandler::toMethodError).toList();
        return ResponseEntity.badRequest().body(Result.failure(
                "request.validation_failed", "请求参数校验失败", errors));
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
