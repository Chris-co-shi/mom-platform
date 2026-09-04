package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.AuthenticationApplication;
import io.github.chrisshi.mom.auth.controller.request.LoginRequest;
import io.github.chrisshi.mom.auth.controller.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController {

    private final AuthenticationApplication authenticationApplication;

    public AuthenticationController(AuthenticationApplication authenticationApplication) {
        this.authenticationApplication = authenticationApplication;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authenticationApplication.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(BearerTokenAuthentication authentication) {
        authenticationApplication.logout(authentication.getToken().getTokenValue());
    }
}
