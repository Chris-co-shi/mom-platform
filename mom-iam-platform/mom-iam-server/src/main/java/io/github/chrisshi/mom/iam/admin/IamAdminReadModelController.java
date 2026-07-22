package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

/** S09 管理页面只读授权快照 API；写操作仍由 S07 {@link IamAdminController} 承担。 */
@RestController
@RequestMapping("/api/iam/admin")
public final class IamAdminReadModelController {
    private final IamAdminReadModelRepository repository;
    private final MomAuthorizationService authorization;

    public IamAdminReadModelController(
            IamAdminReadModelRepository repository,
            MomAuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @GetMapping("/users/{userId}/authorizations")
    IamAdminReadModelRepository.UserAuthorizationView userAuthorization(
            Authentication authentication,
            @PathVariable String userId) {
        authorization.requirePermission(authentication, "iam:user:read");
        return repository.userAuthorization(userId);
    }

    @GetMapping("/roles/{roleId}/permissions")
    IamAdminReadModelRepository.RolePermissionView rolePermissions(
            Authentication authentication,
            @PathVariable String roleId) {
        authorization.requirePermission(authentication, "iam:role:read");
        return repository.rolePermissions(roleId);
    }
}
