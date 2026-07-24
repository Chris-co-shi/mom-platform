package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** S07 IAM 管理 REST API；密码摘要、Token、授权码和私钥永不进入响应 DTO。 */
@RestController
@ConditionalOnBean(IamAdminService.class)
@RequestMapping("/api/iam/admin")
public class IamAdminController {
    private final IamAdminService service;

    public IamAdminController(IamAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    List<IamAdminViews.UserView> users(
            Authentication authentication,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listUsers(authentication, userType, status, limit, offset);
    }

    @GetMapping("/users/{userId}")
    IamAdminViews.UserView user(
            Authentication authentication, @PathVariable String userId) {
        return service.getUser(authentication, userId);
    }

    @GetMapping("/users/{userId}/authorizations")
    IamAdminViews.UserAuthorizationView userAuthorization(
            Authentication authentication, @PathVariable String userId) {
        return service.getUserAuthorization(authentication, userId);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    IamAdminViews.UserView createUser(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody IamAdminService.CreateUser command) {
        return service.createUser(authentication, command, context(request));
    }

    @PutMapping("/users/{userId}")
    IamAdminViews.UserView updateUser(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.UpdateUser command) {
        return service.updateUser(authentication, userId, command, context(request));
    }

    @PutMapping("/users/{userId}/status")
    IamAdminViews.UserView setUserStatus(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.StatusChange command) {
        return service.setUserStatus(authentication, userId, command, context(request));
    }

    @PostMapping("/users/{userId}/unlock")
    IamAdminViews.UserView unlockUser(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.VersionedReason command) {
        return service.unlockUser(authentication, userId, command, context(request));
    }

    @PostMapping("/users/{userId}/credential-reset")
    IamAdminViews.UserView resetCredential(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.PasswordReset command) {
        return service.resetPassword(authentication, userId, command, context(request));
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.VersionedReason command) {
        service.deleteUser(authentication, userId, command, context(request));
    }

    @PutMapping("/users/{userId}/roles")
    IamAdminViews.UserAuthorizationView replaceUserRoles(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.RoleAssignment command) {
        return service.replaceUserRoles(authentication, userId, command, context(request));
    }

    @PutMapping("/users/{userId}/factory-scopes")
    IamAdminViews.UserAuthorizationView replaceFactoryScopes(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.FactoryScopeChange command) {
        return service.replaceFactoryScopes(authentication, userId, command, context(request));
    }

    @PutMapping("/users/{userId}/mobile-access")
    IamAdminViews.UserAuthorizationView setMobileAccess(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.MobileAccessChange command) {
        return service.setMobileAccess(authentication, userId, command, context(request));
    }

    @PutMapping("/users/{userId}/party-binding")
    IamAdminViews.UserAuthorizationView rebindParty(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.PartyRebind command) {
        return service.rebindParty(authentication, userId, command, context(request));
    }

    @GetMapping("/roles")
    List<IamAdminViews.RoleView> roles(
            Authentication authentication,
            @RequestParam(required = false) String userType,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listRoles(authentication, userType, limit, offset);
    }

    @GetMapping("/roles/{roleId}/permissions")
    IamAdminViews.RolePermissionView rolePermissions(
            Authentication authentication, @PathVariable String roleId) {
        return service.getRolePermissions(authentication, roleId);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    IamAdminViews.RoleView createRole(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody IamAdminService.CreateRole command) {
        return service.createRole(authentication, command, context(request));
    }

    @PutMapping("/roles/{roleId}")
    IamAdminViews.RoleView updateRole(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String roleId,
            @RequestBody IamAdminService.UpdateRole command) {
        return service.updateRole(authentication, roleId, command, context(request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    IamAdminViews.RolePermissionView replaceRolePermissions(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String roleId,
            @RequestBody IamAdminService.PermissionAssignment command) {
        return service.replaceRolePermissions(authentication, roleId, command, context(request));
    }

    @GetMapping("/permissions")
    List<IamAdminViews.PermissionView> permissions(
            Authentication authentication,
            @RequestParam(required = false) String domainCode,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listPermissions(authentication, domainCode, limit, offset);
    }

    @GetMapping("/sessions")
    List<IamAdminViews.SessionView> sessions(
            Authentication authentication,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listSessions(authentication, userId, status, limit, offset);
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String sessionId,
            @RequestBody IamAdminService.Reason command) {
        service.revokeSession(authentication, sessionId, command, context(request));
    }

    @PostMapping("/users/{userId}/sessions/revoke")
    Map<String, Integer> revokeAllSessions(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.Reason command) {
        return Map.of("revoked", service.revokeAllSessions(
                authentication, userId, command, context(request)));
    }

    @GetMapping("/security-audit")
    List<IamAdminViews.SecurityAuditView> securityAudit(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listAudit(authentication, category, targetId, limit, offset);
    }

    @GetMapping("/oauth-clients")
    List<IamAdminViews.ClientView> clients(Authentication authentication) {
        return service.listClients(authentication);
    }

    @PutMapping("/oauth-clients/{clientId}/status")
    IamAdminViews.ClientView setClientStatus(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String clientId,
            @RequestBody IamAdminService.ClientStatusChange command) {
        return service.setClientStatus(authentication, clientId, command, context(request));
    }

    private static IamAdminService.RequestContext context(HttpServletRequest request) {
        return new IamAdminService.RequestContext(
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : request.getHeader("User-Agent"));
    }
}
