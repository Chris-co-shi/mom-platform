package io.github.chrisshi.mom.iam.admin;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** S09 只读投影 API 的稳定错误映射。 */
@RestControllerAdvice(assignableTypes = IamAdminReadModelController.class)
public final class IamAdminReadModelExceptionHandler {

    @ExceptionHandler(IamAdminExceptions.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(IamAdminExceptions.NotFound exception) {
        return error("not_found", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> forbidden() {
        return error("forbidden", "缺少读取该授权快照的 Permission");
    }

    private static Map<String, String> error(String code, String message) {
        return Map.of("code", code, "message", message == null ? code : message);
    }
}
