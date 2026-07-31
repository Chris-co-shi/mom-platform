package io.github.chrisshi.mom.iam.web.admin.session;

import io.github.chrisshi.mom.iam.application.admin.IamSessionAdminApplicationService;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.web.admin.IamAdminWebSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** IAM Session 管理 REST Adapter。 */
@RestController
@ConditionalOnBean(IamSessionAdminApplicationService.class)
@RequestMapping("/api/iam/admin")
public class IamSessionAdminController {
    private final IamSessionAdminApplicationService sessions;
    private final IamAdminWebSupport web;

    public IamSessionAdminController(
            IamSessionAdminApplicationService sessions, IamAdminWebSupport web) {
        this.sessions = sessions;
        this.web = web;
    }

    @GetMapping("/sessions")
    List<IamAdminViews.SessionView> sessions(
            Authentication authentication,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return sessions.listSessions(
                web.actor(authentication), userId, status, limit, offset);
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String sessionId,
            @RequestBody IamAdminCommands.Reason command) {
        sessions.revokeSession(
                web.actor(authentication), sessionId, command, web.request(request));
    }

    @PostMapping("/users/{userId}/sessions/revoke")
    Map<String, Integer> revokeAllSessions(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.Reason command) {
        return Map.of("revoked", sessions.revokeAllSessions(
                web.actor(authentication), userId, command, web.request(request)));
    }
}
