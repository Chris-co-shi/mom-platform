package io.github.chrisshi.mom.iam.web.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/** Spring Security/Servlet 入站对象到 Application 模型的 Web Adapter。 */
public final class IamAdminWebSupport {
    private final MomAuthorizationService authorization;

    public IamAdminWebSupport(MomAuthorizationService authorization) {
        this.authorization = authorization;
    }

    public IamAdminActor actor(Authentication authentication) {
        MomJwtAuthorization current = authorization.current(authentication);
        return new IamAdminActor(
                current.userId(), current.sessionId(), current.clientId(),
                current.permissions());
    }

    public IamAdminRequestContext request(HttpServletRequest request) {
        return new IamAdminRequestContext(
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : request.getHeader("User-Agent"));
    }
}
