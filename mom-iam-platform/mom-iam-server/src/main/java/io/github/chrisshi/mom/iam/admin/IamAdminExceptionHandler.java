package io.github.chrisshi.mom.iam.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** S07 管理 API 稳定错误映射；不回显 SQL、凭证或内部堆栈。 */
@RestControllerAdvice(assignableTypes = IamAdminController.class)
@ConditionalOnBean(IamAdminService.class)
public class IamAdminExceptionHandler {

    @ExceptionHandler(IamAdminExceptions.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(IamAdminExceptions.NotFound exception) {
        return error("not_found", exception.getMessage());
    }

    @ExceptionHandler(IamAdminExceptions.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> staleVersion(IamAdminExceptions.StaleVersion exception) {
        return error("stale_version", exception.getMessage());
    }

    @ExceptionHandler({IamAdminExceptions.Conflict.class, DataIntegrityViolationException.class,
            IllegalStateException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(Exception exception) {
        String message = exception instanceof DataIntegrityViolationException
                ? "操作违反唯一性、引用或并发约束" : exception.getMessage();
        return error("conflict", message);
    }

    @ExceptionHandler(IamAdminExceptions.DependencyUnavailable.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, String> unavailable(IamAdminExceptions.DependencyUnavailable exception) {
        return error("dependency_unavailable", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(IllegalArgumentException exception) {
        return error("invalid_request", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> forbidden(AccessDeniedException exception) {
        return error("forbidden", "缺少执行该管理操作的 Permission");
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("code", code, "message", message == null ? code : message);
    }
}
