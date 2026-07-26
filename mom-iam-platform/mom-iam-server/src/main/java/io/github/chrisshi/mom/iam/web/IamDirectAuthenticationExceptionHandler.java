package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 第一方认证 API 的稳定、无敏感信息错误映射。 */
@RestControllerAdvice(assignableTypes = IamDirectAuthenticationController.class)
public final class IamDirectAuthenticationExceptionHandler {

    @ExceptionHandler(IamDirectAuthenticationController.InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, "invalid_credentials", exception.getMessage());
    }

    @ExceptionHandler(IamDirectAuthenticationController.PasswordChangeRequiredException.class)
    ResponseEntity<ErrorResponse> passwordChangeRequired(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, "password_change_required", exception.getMessage());
    }

    @ExceptionHandler(IamDirectAuthenticationController.PasswordChangeNotRequiredException.class)
    ResponseEntity<ErrorResponse> passwordChangeNotRequired(RuntimeException exception) {
        return response(HttpStatus.CONFLICT, "password_change_not_required", exception.getMessage());
    }

    @ExceptionHandler(IamDirectAuthenticationController.InvalidAuthenticationRequestException.class)
    ResponseEntity<ErrorResponse> invalidRequest(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "invalid_authentication_request", exception.getMessage());
    }

    @ExceptionHandler(IamClientAccessPolicyService.AccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, "client_access_forbidden", exception.getMessage());
    }

    @ExceptionHandler(IamSessionTokenService.RefreshReplayDetectedException.class)
    ResponseEntity<ErrorResponse> refreshReplay(RuntimeException exception) {
        return response(HttpStatus.UNAUTHORIZED, "refresh_reuse_detected", exception.getMessage());
    }

    @ExceptionHandler(OAuth2AuthenticationException.class)
    ResponseEntity<ErrorResponse> oauthFailure(OAuth2AuthenticationException exception) {
        String code = exception.getError() == null
                ? "invalid_grant" : exception.getError().getErrorCode();
        return response(HttpStatus.UNAUTHORIZED, code, "Refresh Token 无效或 Session 已终止");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> validationFailure(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "credential_policy_violation", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> stateConflict(IllegalStateException exception) {
        return response(HttpStatus.CONFLICT, "authentication_state_conflict", exception.getMessage());
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status,
            String error,
            String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(error, message));
    }

    public record ErrorResponse(String error, String message) {
    }
}
