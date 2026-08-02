package io.github.chrisshi.mom.iam.web.admin.role;

import io.github.chrisshi.mom.iam.application.admin.IamRoleAdminApplicationService;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.web.admin.IamAdminWebSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** IAM Role 与 Permission 目录管理 REST Adapter。 */
@RestController
@ConditionalOnBean(IamRoleAdminApplicationService.class)
@RequestMapping("/api/iam/admin")
public class IamRoleAdminController {
    private final IamRoleAdminApplicationService roles;
    private final IamAdminWebSupport web;

    public IamRoleAdminController(
            IamRoleAdminApplicationService roles, IamAdminWebSupport web) {
        this.roles = roles;
        this.web = web;
    }

    @GetMapping("/roles")
    List<IamAdminViews.RoleView> roles(
            Authentication authentication,
            @RequestParam(required = false) String userType,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return roles.listRoles(web.actor(authentication), userType, limit, offset);
    }

    @GetMapping("/roles/{roleId}/permissions")
    IamAdminViews.RolePermissionView rolePermissions(
            Authentication authentication, @PathVariable String roleId) {
        return roles.getRolePermissions(web.actor(authentication), roleId);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    IamAdminViews.RoleView createRole(
            Authentication authentication, HttpServletRequest request,
            @RequestBody IamAdminCommands.CreateRole command) {
        return roles.createRole(web.actor(authentication), command, web.request(request));
    }

    @PutMapping("/roles/{roleId}")
    IamAdminViews.RoleView updateRole(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String roleId,
            @RequestBody IamAdminCommands.UpdateRole command) {
        return roles.updateRole(
                web.actor(authentication), roleId, command, web.request(request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    IamAdminViews.RolePermissionView replaceRolePermissions(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String roleId,
            @RequestBody IamAdminCommands.PermissionAssignment command) {
        return roles.replaceRolePermissions(
                web.actor(authentication), roleId, command, web.request(request));
    }

    @GetMapping("/permissions")
    List<IamAdminViews.PermissionView> permissions(
            Authentication authentication,
            @RequestParam(required = false) String domainCode,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return roles.listPermissions(
                web.actor(authentication), domainCode, limit, offset);
    }
}
