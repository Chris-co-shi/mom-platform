package io.github.chrisshi.mom.iam.admin;

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
import java.util.Set;

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
    List<IamAdminJdbcRepository.UserRow> users(
            Authentication authentication,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listUsers(authentication, userType, status, limit, offset);
    }

    @GetMapping("/users/{userId}")
    IamAdminJdbcRepository.UserRow user(
            Authentication authentication, @PathVariable String userId) {
        return service.getUser(authentication, userId);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    IamAdminJdbcRepository.UserRow createUser(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody IamAdminService.CreateUser command) {
        return service.createUser(authentication, command, context(request));
    }

    @PutMapping("/users/{userId}")
    IamAdminJdbcRepository.UserRow updateUser(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.UpdateUser command) {
        return service.updateUser(authentication, userId, command, context(request));
    }

    @PutMapping("/users/{userId}/status")
    IamAdminJdbcRepository.UserRow setUserStatus(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.StatusChange command) {
        return service.setUserStatus(authentication, userId, command, context(request));
    }

    @PostMapping("/users/{userId}/unlock")
    IamAdminJdbcRepository.UserRow unlockUser(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.VersionedReason command) {
        return service.unlockUser(authentication, userId, command, context(request));
    }

    @PostMapping("/users/{userId}/credential-reset")
    IamAdminJdbcRepository.UserRow resetCredential(
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
    Map<String, Set<String>> replaceUserRoles(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.RoleAssignment command) {
        return Map.of("roleIds", service.replaceUserRoles(
                authentication, userId, command, context(request)));
    }

    @PutMapping("/users/{userId}/factory-scopes")
    Map<String, Set<String>> replaceFactoryScopes(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.FactoryScopeChange command) {
        return Map.of("factoryIds", service.replaceFactoryScopes(
                authentication, userId, command, context(request)));
    }

    @PutMapping("/users/{userId}/mobile-access")
    Map<String, Boolean> setMobileAccess(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.MobileAccessChange command) {
        return Map.of("enabled", service.setMobileAccess(
                authentication, userId, command, context(request)));
    }

    @PutMapping("/users/{userId}/party-binding")
    IamAdminJdbcRepository.PartyBindingRow rebindParty(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminService.PartyRebind command) {
        return service.rebindParty(authentication, userId, command, context(request));
    }

    @GetMapping("/roles")
    List<IamAdminJdbcRepository.RoleRow> roles(
            Authentication authentication,
            @RequestParam(required = false) String userType,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listRoles(authentication, userType, limit, offset);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    IamAdminJdbcRepository.RoleRow createRole(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody IamAdminService.CreateRole command) {
        return service.createRole(authentication, command, context(request));
    }

    @PutMapping("/roles/{roleId}")
    IamAdminJdbcRepository.RoleRow updateRole(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String roleId,
            @RequestBody IamAdminService.UpdateRole command) {
        return service.updateRole(authentication, roleId, command, context(request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    Map<String, Set<String>> replaceRolePermissions(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable String roleId,
            @RequestBody IamAdminService.PermissionAssignment command) {
        return Map.of("permissionIds", service.replaceRolePermissions(
                authentication, roleId, command, context(request)));
    }

    @GetMapping("/permissions")
    List<IamAdminJdbcRepository.PermissionRow> permissions(
            Authentication authentication,
            @RequestParam(required = false) String domainCode,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listPermissions(authentication, domainCode, limit, offset);
    }

    @GetMapping("/sessions")
    List<IamAdminJdbcRepository.SessionRow> sessions(
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
    List<IamAdminJdbcRepository.AuditRow> securityAudit(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listAudit(authentication, category, targetId, limit, offset);
    }

    @GetMapping("/oauth-clients")
    List<IamAdminJdbcRepository.ClientRow> clients(Authentication authentication) {
        return service.listClients(authentication);
    }

    @PutMapping("/oauth-clients/{clientId}/status")
    IamAdminJdbcRepository.ClientRow setClientStatus(
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
