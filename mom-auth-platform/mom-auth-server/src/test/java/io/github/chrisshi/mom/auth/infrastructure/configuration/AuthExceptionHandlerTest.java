package io.github.chrisshi.mom.auth.infrastructure.configuration;

import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler((key, locale, args) -> key);

    @Test
    void businessInputAndStateConflictsMustKeepRealHttpStatus() {
        assertStatus(AuthErrorCode.CURRENT_PASSWORD_INVALID, HttpStatus.BAD_REQUEST);
        assertStatus(AuthErrorCode.RELATION_SELECTION_TOO_LARGE, HttpStatus.BAD_REQUEST);
        assertStatus(AuthErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        assertStatus(AuthErrorCode.ROLE_DISABLED, HttpStatus.CONFLICT);
        assertStatus(AuthErrorCode.PERMISSION_DISABLED, HttpStatus.CONFLICT);
        assertStatus(AuthErrorCode.RESOURCE_REFERENCED, HttpStatus.CONFLICT);
        assertStatus(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT, HttpStatus.CONFLICT);
    }

    private void assertStatus(AuthErrorCode errorCode, HttpStatus expected) {
        var response = handler.handleAuthException(new AuthException(errorCode), Locale.SIMPLIFIED_CHINESE);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(errorCode.code());
    }
}
