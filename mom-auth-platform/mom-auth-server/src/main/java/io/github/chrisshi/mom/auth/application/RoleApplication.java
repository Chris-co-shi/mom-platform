package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.auth.application.model.PermissionView;
import io.github.chrisshi.mom.auth.application.model.RoleView;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RolePermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserRoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RoleMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RolePermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserRoleMapper;
import io.github.chrisshi.mom.core.page.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 角色管理及 Role-Permission 关系编排。 */
@Component
public class RoleApplication {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    public RoleApplication(
        RoleMapper roleMapper,
        UserRoleMapper userRoleMapper,
        RolePermissionMapper rolePermissionMapper,
        PermissionMapper permissionMapper
    ) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    @Transactional
    public RoleView create(String code, String name, String description, boolean enabled) {
        String normalizedCode = code.strip();
        ensureCodeAvailable(normalizedCode);
        RoleEntity entity = new RoleEntity();
        entity.setCode(normalizedCode);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setEnabled(enabled);
        try {
            roleMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new AuthException(AuthErrorCode.ROLE_CODE_CONFLICT, AuthErrorCode.ROLE_CODE_CONFLICT.defaultMessage(), exception);
        }
        return toView(entity);
    }

    public RoleView get(String id) {
        return toView(requireRole(id));
    }

    public PageResult<RoleView> list(long pageNo, int pageSize) {
        long total = roleMapper.countActive();
        long totalPages = totalPages(total, pageSize);
        if (total == 0 || pageNo > totalPages) {
            return new PageResult<>(List.of(), pageNo, pageSize, total, totalPages);
        }
        long offset = (pageNo - 1) * pageSize;
        List<RoleView> records = roleMapper.selectPage(pageSize, offset).stream()
            .map(RoleApplication::toView)
            .toList();
        return new PageResult<>(records, pageNo, pageSize, total, totalPages);
    }

    @Transactional
    public RoleView update(String id, String name, String description, boolean enabled, long version) {
        RoleEntity entity = requireRole(id);
        requireVersion(entity.getVersion(), version);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setEnabled(enabled);
        if (roleMapper.updateById(entity) != 1) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return toView(entity);
    }

    @Transactional
    public void delete(String id) {
        requireRole(id);
        long userReferences = userRoleMapper.selectCount(
            new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getRoleId, id)
        );
        long permissionReferences = rolePermissionMapper.selectCount(
            new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, id)
        );
        if (userReferences > 0 || permissionReferences > 0) {
            throw new AuthException(AuthErrorCode.RESOURCE_REFERENCED, "角色仍存在用户或权限关系，请先解除关联");
        }
        roleMapper.deleteById(id);
    }

    public List<PermissionView> permissions(String roleId) {
        requireRole(roleId);
        List<String> permissionIds = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId)
        ).stream().map(RolePermissionEntity::getPermissionId).toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectBatchIds(permissionIds).stream()
            .sorted(java.util.Comparator.comparing(PermissionEntity::getCode).thenComparing(PermissionEntity::getId))
            .map(RoleApplication::toPermissionView)
            .toList();
    }

    @Transactional
    public List<PermissionView> replacePermissions(String roleId, List<String> requestedPermissionIds) {
        requireRole(roleId);
        Set<String> permissionIds = new LinkedHashSet<>(requestedPermissionIds);
        validatePermissions(permissionIds);

        rolePermissionMapper.delete(
            new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId)
        );
        for (String permissionId : permissionIds) {
            RolePermissionEntity relation = new RolePermissionEntity();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        return permissions(roleId);
    }

    private RoleEntity requireRole(String id) {
        RoleEntity entity = roleMapper.selectById(id);
        if (entity == null) {
            throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return entity;
    }

    private void ensureCodeAvailable(String code) {
        long count = roleMapper.selectCount(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, code));
        if (count > 0) {
            throw new AuthException(AuthErrorCode.ROLE_CODE_CONFLICT);
        }
    }

    private void validatePermissions(Set<String> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<PermissionEntity> permissions = permissionMapper.selectBatchIds(permissionIds);
        Set<String> found = permissions.stream().map(PermissionEntity::getId).collect(java.util.stream.Collectors.toSet());
        for (String permissionId : permissionIds) {
            if (!found.contains(permissionId)) {
                throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限不存在: " + permissionId);
            }
        }
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long totalPages(long total, int pageSize) {
        return total == 0 ? 0 : ((total - 1) / pageSize) + 1;
    }

    private static void requireVersion(Long actual, long expected) {
        if (actual == null || actual.longValue() != expected) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private static RoleView toView(RoleEntity entity) {
        return new RoleView(
            entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
            Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private static PermissionView toPermissionView(PermissionEntity entity) {
        return new PermissionView(
            entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
            Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
