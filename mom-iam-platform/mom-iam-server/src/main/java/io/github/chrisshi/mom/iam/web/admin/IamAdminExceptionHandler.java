package io.github.chrisshi.mom.iam.web.admin;

import io.github.chrisshi.mom.iam.application.admin.IamAdminExceptions;
import io.github.chrisshi.mom.iam.web.admin.audit.IamSecurityAuditController;
import io.github.chrisshi.mom.iam.web.admin.client.IamClientAdminController;
import io.github.chrisshi.mom.iam.web.admin.role.IamRoleAdminController;
import io.github.chrisshi.mom.iam.web.admin.session.IamSessionAdminController;
import io.github.chrisshi.mom.iam.web.admin.user.IamUserAdminController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/** IAM Admin 稳定错误映射；不回显 SQL、凭证或内部堆栈。 */
@ConditionalOnBean(io.github.chrisshi.mom.iam.application.admin.IamUserAdminApplicationService.class)
@RestControllerAdvice(assignableTypes = {
        IamUserAdminController.class,
        IamRoleAdminController.class,
        IamSessionAdminController.class,
        IamClientAdminController.class,
        IamSecurityAuditController.class
})
public class IamAdminExceptionHandler {

    @ExceptionHandler(IamAdminExceptions.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    IamAdminErrorResponse notFound(IamAdminExceptions.NotFound exception) {
        return error("not_found", exception.getMessage());
    }

    @ExceptionHandler(IamAdminExceptions.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    IamAdminErrorResponse staleVersion(IamAdminExceptions.StaleVersion exception) {
        return error("stale_version", exception.getMessage());
    }

    @ExceptionHandler({
            IamAdminExceptions.Conflict.class,
            DataIntegrityViolationException.class,
            IllegalStateException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    IamAdminErrorResponse conflict(Exception exception) {
        String message = exception instanceof DataIntegrityViolationException
                ? "操作违反唯一性、引用或并发约束" : exception.getMessage();
        return error("conflict", message);
    }

    @ExceptionHandler(IamAdminExceptions.DependencyUnavailable.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    IamAdminErrorResponse unavailable(
            IamAdminExceptions.DependencyUnavailable exception) {
        return error("dependency_unavailable", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    IamAdminErrorResponse badRequest(IllegalArgumentException exception) {
        return error("invalid_request", exception.getMessage());
    }

    @ExceptionHandler({IamAdminExceptions.Forbidden.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    IamAdminErrorResponse forbidden(Exception exception) {
        return error("forbidden", "缺少执行该管理操作的 Permission");
    }

    private static IamAdminErrorResponse error(String code, String message) {
        return new IamAdminErrorResponse(code, message == null ? code : message);
    }
}
