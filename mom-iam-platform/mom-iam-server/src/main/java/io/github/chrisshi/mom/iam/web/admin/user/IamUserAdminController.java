package io.github.chrisshi.mom.iam.web.admin.user;

import io.github.chrisshi.mom.iam.application.admin.IamUserAdminApplicationService;
import io.github.chrisshi.mom.iam.application.admin.IamUserAuthorizationApplicationService;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.web.admin.IamAdminWebSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** IAM 用户账号与授权关系管理 REST Adapter。 */
@RestController
@ConditionalOnBean(IamUserAdminApplicationService.class)
@RequestMapping("/api/iam/admin")
public class IamUserAdminController {
    private final IamUserAdminApplicationService users;
    private final IamUserAuthorizationApplicationService authorizations;
    private final IamAdminWebSupport web;

    public IamUserAdminController(
            IamUserAdminApplicationService users,
            IamUserAuthorizationApplicationService authorizations,
            IamAdminWebSupport web) {
        this.users = users;
        this.authorizations = authorizations;
        this.web = web;
    }

    @GetMapping("/users")
    List<IamAdminViews.UserView> users(
            Authentication authentication,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return users.listUsers(web.actor(authentication), userType, status, limit, offset);
    }

    @GetMapping("/users/{userId}")
    IamAdminViews.UserView user(
            Authentication authentication, @PathVariable String userId) {
        return users.getUser(web.actor(authentication), userId);
    }

    @GetMapping("/users/{userId}/authorizations")
    IamAdminViews.UserAuthorizationView userAuthorization(
            Authentication authentication, @PathVariable String userId) {
        return authorizations.getUserAuthorization(web.actor(authentication), userId);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    IamAdminViews.UserView createUser(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody IamAdminCommands.CreateUser command) {
        return users.createUser(web.actor(authentication), command, web.request(request));
    }

    @PutMapping("/users/{userId}")
    IamAdminViews.UserView updateUser(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.UpdateUser command) {
        return users.updateUser(
                web.actor(authentication), userId, command, web.request(request));
    }

    @PutMapping("/users/{userId}/status")
    IamAdminViews.UserView setUserStatus(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.StatusChange command) {
        return users.setUserStatus(
                web.actor(authentication), userId, command, web.request(request));
    }

    @PostMapping("/users/{userId}/unlock")
    IamAdminViews.UserView unlockUser(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.VersionedReason command) {
        return users.unlockUser(
                web.actor(authentication), userId, command, web.request(request));
    }

    @PostMapping("/users/{userId}/credential-reset")
    IamAdminViews.UserView resetCredential(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.PasswordReset command) {
        return users.resetPassword(
                web.actor(authentication), userId, command, web.request(request));
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.VersionedReason command) {
        users.deleteUser(web.actor(authentication), userId, command, web.request(request));
    }

    @PutMapping("/users/{userId}/roles")
    IamAdminViews.UserAuthorizationView replaceUserRoles(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.RoleAssignment command) {
        return authorizations.replaceUserRoles(
                web.actor(authentication), userId, command, web.request(request));
    }

    @PutMapping("/users/{userId}/factory-scopes")
    IamAdminViews.UserAuthorizationView replaceFactoryScopes(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.FactoryScopeChange command) {
        return authorizations.replaceFactoryScopes(
                web.actor(authentication), userId, command, web.request(request));
    }

    @PutMapping("/users/{userId}/mobile-access")
    IamAdminViews.UserAuthorizationView setMobileAccess(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.MobileAccessChange command) {
        return authorizations.setMobileAccess(
                web.actor(authentication), userId, command, web.request(request));
    }

    @PutMapping("/users/{userId}/party-binding")
    IamAdminViews.UserAuthorizationView rebindParty(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody IamAdminCommands.PartyRebind command) {
        return authorizations.rebindParty(
                web.actor(authentication), userId, command, web.request(request));
    }
}
