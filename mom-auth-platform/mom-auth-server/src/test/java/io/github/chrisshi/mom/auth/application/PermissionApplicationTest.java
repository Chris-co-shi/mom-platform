package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RolePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionApplicationTest {

    private PermissionMapper permissionMapper;
    private RolePermissionMapper rolePermissionMapper;
    private PermissionApplication application;

    @BeforeEach
    void setUp() {
        permissionMapper = mock(PermissionMapper.class);
        rolePermissionMapper = mock(RolePermissionMapper.class);
        application = new PermissionApplication(permissionMapper, rolePermissionMapper);
    }

    @Test
    void createAndUpdateMustNormalizeValuesAndProtectUniqueCode() {
        when(permissionMapper.insert(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity entity = invocation.getArgument(0);
            entity.setId("permission-1");
            return 1;
        });
        var created = application.create(" auth:user:read ", " User Read ", "  description  ", true);
        assertThat(created.code()).isEqualTo("auth:user:read");
        assertThat(created.name()).isEqualTo("User Read");
        assertThat(created.description()).isEqualTo("description");

        PermissionEntity permission = permission("permission-1", true, 2L);
        when(permissionMapper.selectById("permission-1")).thenReturn(permission);
        when(permissionMapper.updateById(permission)).thenReturn(1);
        var updated = application.update("permission-1", " New Name ", " ", false, 2L);
        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.description()).isNull();
        assertThat(updated.enabled()).isFalse();

        when(permissionMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.create("auth:user:read", "Read", null, true),
            AuthErrorCode.PERMISSION_CODE_CONFLICT);
    }

    @Test
    void createMustMapConcurrentCodeConflict() {
        doThrow(new DuplicateKeyException("uk_auth_permission_code"))
            .when(permissionMapper).insert(any(PermissionEntity.class));
        assertError(() -> application.create("auth:user:read", "Read", null, true),
            AuthErrorCode.PERMISSION_CODE_CONFLICT);
    }

    @Test
    void enableDisableAndOptimisticLockMustBeExplicit() {
        PermissionEntity enabled = permission("permission-1", true, 0L);
        when(permissionMapper.selectById("permission-1")).thenReturn(enabled);
        assertThat(application.enable("permission-1", 0L).enabled()).isTrue();
        verify(permissionMapper, never()).updateById(enabled);

        PermissionEntity disabled = permission("permission-2", false, 0L);
        when(permissionMapper.selectById("permission-2")).thenReturn(disabled);
        when(permissionMapper.updateById(disabled)).thenReturn(1);
        assertThat(application.enable("permission-2", 0L).enabled()).isTrue();

        PermissionEntity active = permission("permission-3", true, 0L);
        when(permissionMapper.selectById("permission-3")).thenReturn(active);
        when(permissionMapper.updateById(active)).thenReturn(1);
        assertThat(application.disable("permission-3", 0L).enabled()).isFalse();

        PermissionEntity stale = permission("permission-4", true, 3L);
        when(permissionMapper.selectById("permission-4")).thenReturn(stale);
        assertError(() -> application.disable("permission-4", 2L),
            AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
    }

    @Test
    void affectedRowsZeroMustDistinguishMissingResourceFromVersionConflict() {
        PermissionEntity permission = permission("permission-1", true, 1L);
        when(permissionMapper.selectById("permission-1")).thenReturn(permission, permission);
        when(permissionMapper.updateById(permission)).thenReturn(0);
        assertError(() -> application.update("permission-1", "Name", null, true, 1L),
            AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);

        PermissionEntity deleted = permission("permission-2", true, 1L);
        when(permissionMapper.selectById("permission-2")).thenReturn(deleted, null);
        when(permissionMapper.updateById(deleted)).thenReturn(0);
        assertError(() -> application.update("permission-2", "Name", null, true, 1L),
            AuthErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteMustRejectReferencedPermissionAndDeleteUnreferencedPermission() {
        when(permissionMapper.selectById("referenced")).thenReturn(permission("referenced", true, 0L));
        when(rolePermissionMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.delete("referenced"), AuthErrorCode.RESOURCE_REFERENCED);
        verify(permissionMapper, never()).deleteById("referenced");

        when(permissionMapper.selectById("free")).thenReturn(permission("free", true, 0L));
        when(rolePermissionMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.deleteById("free")).thenReturn(1);
        application.delete("free");
        verify(permissionMapper).deleteById("free");
    }

    private static PermissionEntity permission(String id, boolean enabled, long version) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(id);
        entity.setCode(id);
        entity.setName(id);
        entity.setEnabled(enabled);
        entity.setVersion(version);
        return entity;
    }

    private static void assertError(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(AuthException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
