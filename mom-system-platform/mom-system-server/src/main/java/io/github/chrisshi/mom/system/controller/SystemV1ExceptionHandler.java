package io.github.chrisshi.mom.system.controller;

import io.github.chrisshi.mom.system.application.SystemV1Exception;
import io.github.chrisshi.mom.system.controller.dictionary.DictionaryController;
import io.github.chrisshi.mom.system.controller.i18n.I18nManagementController;
import io.github.chrisshi.mom.system.controller.i18n.SupportedLocaleController;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Locale;

/**
 * System V1 Controller 的统一脱敏、本地化异常边界。
 *
 * <p>业务异常保存稳定 code/messageKey/args，最终展示文本只在 HTTP 边界通过 {@link I18nMessageResolver}
 * 生成。数据库异常统一映射 503，不回显 SQL、约束名或连接信息。认证与授权异常继续由 Security Filter
 * Chain 处理，不假设进入 ControllerAdvice。</p>
 */
@RestControllerAdvice(assignableTypes = {
        DictionaryController.class,
        SupportedLocaleController.class,
        I18nManagementController.class
})
public class SystemV1ExceptionHandler {
    private final I18nMessageResolver messageResolver;

    /** @param messageResolver Framework/System 组合消息解析器 */
    public SystemV1ExceptionHandler(I18nMessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    /** 将不存在资源映射为 404。 */
    @ExceptionHandler(SystemV1Exception.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> notFound(SystemV1Exception exception, Locale locale) {
        return businessError(exception, locale);
    }

    /** 将唯一、状态、placeholder 与版本冲突映射为 409。 */
    @ExceptionHandler(SystemV1Exception.Conflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> conflict(SystemV1Exception exception, Locale locale) {
        return businessError(exception, locale);
    }

    /** 将非法入参、绑定与反序列化问题映射为稳定 400。 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalidRequest(Exception exception, Locale locale) {
        return Result.failure("invalid_request",
                messageResolver.resolve("framework.web.invalid_request", locale));
    }

    /** 将本地权威数据库不可用映射为 503，避免伪造 Runtime Bundle。 */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> dataUnavailable(DataAccessException exception, Locale locale) {
        return Result.failure("dependency_unavailable",
                messageResolver.resolve("framework.error.internal", locale));
    }

    private Result<Void> businessError(SystemV1Exception exception, Locale locale) {
        return Result.failure(exception.code(),
                messageResolver.resolve(exception.messageKey(), locale, exception.args()));
    }
}
