package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.auth.application.model.PageView;
import io.github.chrisshi.mom.auth.application.model.PermissionView;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RolePermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RolePermissionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PermissionApplication {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public PermissionApplication(PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Transactional
    public PermissionView create(String code, String name, String description, boolean enabled) {
        String normalizedCode = code.strip();
        ensureCodeAvailable(normalizedCode);
        PermissionEntity entity = new PermissionEntity();
        entity.setCode(normalizedCode);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setEnabled(enabled);
        try {
            permissionMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new AuthException(
                AuthErrorCode.PERMISSION_CODE_CONFLICT,
                AuthErrorCode.PERMISSION_CODE_CONFLICT.defaultMessage(),
                exception
            );
        }
        return toView(entity);
    }

    public PermissionView get(String id) {
        return toView(requirePermission(id));
    }

    public PageView<PermissionView> list(int limit, long offset) {
        List<PermissionView> items = permissionMapper.selectPage(limit, offset).stream()
            .map(PermissionApplication::toView)
            .toList();
        return new PageView<>(items, permissionMapper.countActive());
    }

    @Transactional
    public PermissionView update(String id, String name, String description, boolean enabled, long version) {
        PermissionEntity entity = requirePermission(id);
        requireVersion(entity.getVersion(), version);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setEnabled(enabled);
        if (permissionMapper.updateById(entity) != 1) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return toView(entity);
    }

    @Transactional
    public void delete(String id) {
        requirePermission(id);
        long references = rolePermissionMapper.selectCount(
            new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getPermissionId, id)
        );
        if (references > 0) {
            throw new AuthException(AuthErrorCode.RESOURCE_REFERENCED, "权限仍被角色引用，请先解除角色权限关系");
        }
        permissionMapper.deleteById(id);
    }

    private PermissionEntity requirePermission(String id) {
        PermissionEntity entity = permissionMapper.selectById(id);
        if (entity == null) {
            throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限不存在");
        }
        return entity;
    }

    private void ensureCodeAvailable(String code) {
        long count = permissionMapper.selectCount(
            new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getCode, code)
        );
        if (count > 0) {
            throw new AuthException(AuthErrorCode.PERMISSION_CODE_CONFLICT);
        }
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireVersion(Long actual, long expected) {
        if (actual == null || actual.longValue() != expected) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private static PermissionView toView(PermissionEntity entity) {
        return new PermissionView(
            entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
            Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
