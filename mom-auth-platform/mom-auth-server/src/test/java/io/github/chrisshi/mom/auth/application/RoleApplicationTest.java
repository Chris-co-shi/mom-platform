package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RolePermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RoleMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RolePermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleApplicationTest {

    private RoleMapper roleMapper;
    private UserRoleMapper userRoleMapper;
    private RolePermissionMapper rolePermissionMapper;
    private PermissionMapper permissionMapper;
    private RoleApplication application;

    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        rolePermissionMapper = mock(RolePermissionMapper.class);
        permissionMapper = mock(PermissionMapper.class);
        application = new RoleApplication(roleMapper, userRoleMapper, rolePermissionMapper, permissionMapper);
    }

    @Test
    void createAndUpdateMustNormalizeValuesAndProtectUniqueCode() {
        when(roleMapper.insert(any(RoleEntity.class))).thenAnswer(invocation -> {
            RoleEntity entity = invocation.getArgument(0);
            entity.setId("role-1");
            return 1;
        });
        var created = application.create(" PLATFORM_ADMIN ", " Admin ", "  description  ", true);
        assertThat(created.code()).isEqualTo("PLATFORM_ADMIN");
        assertThat(created.name()).isEqualTo("Admin");
        assertThat(created.description()).isEqualTo("description");

        RoleEntity role = role("role-1", "PLATFORM_ADMIN", true, 2L);
        when(roleMapper.selectById("role-1")).thenReturn(role);
        when(roleMapper.updateById(role)).thenReturn(1);
        var updated = application.update("role-1", " New Admin ", " ", false, 2L);
        assertThat(updated.name()).isEqualTo("New Admin");
        assertThat(updated.description()).isNull();
        assertThat(updated.enabled()).isFalse();

        when(roleMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.create("PLATFORM_ADMIN", "Admin", null, true),
            AuthErrorCode.ROLE_CODE_CONFLICT);
    }

    @Test
    void createMustMapConcurrentCodeConflict() {
        doThrow(new DuplicateKeyException("uk_auth_role_code"))
            .when(roleMapper).insert(any(RoleEntity.class));
        assertError(() -> application.create("ROLE", "Role", null, true),
            AuthErrorCode.ROLE_CODE_CONFLICT);
    }

    @Test
    void enableDisableAndOptimisticLockMustBeExplicit() {
        RoleEntity enabled = role("role-1", "A", true, 0L);
        when(roleMapper.selectById("role-1")).thenReturn(enabled);
        assertThat(application.enable("role-1", 0L).enabled()).isTrue();
        verify(roleMapper, never()).updateById(enabled);

        RoleEntity disabled = role("role-2", "B", false, 0L);
        when(roleMapper.selectById("role-2")).thenReturn(disabled);
        when(roleMapper.updateById(disabled)).thenReturn(1);
        assertThat(application.enable("role-2", 0L).enabled()).isTrue();

        RoleEntity active = role("role-3", "C", true, 0L);
        when(roleMapper.selectById("role-3")).thenReturn(active);
        when(roleMapper.updateById(active)).thenReturn(1);
        assertThat(application.disable("role-3", 0L).enabled()).isFalse();

        RoleEntity stale = role("role-4", "D", true, 3L);
        when(roleMapper.selectById("role-4")).thenReturn(stale);
        assertError(() -> application.disable("role-4", 2L), AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
    }

    @Test
    void deleteMustProtectBothRelationshipDirections() {
        when(roleMapper.selectById("role-1")).thenReturn(role("role-1", "A", true, 0L));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.delete("role-1"), AuthErrorCode.RESOURCE_REFERENCED);

        when(roleMapper.selectById("role-2")).thenReturn(role("role-2", "B", true, 0L));
        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(rolePermissionMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.delete("role-2"), AuthErrorCode.RESOURCE_REFERENCED);

        when(roleMapper.selectById("role-3")).thenReturn(role("role-3", "C", true, 0L));
        when(rolePermissionMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.deleteById("role-3")).thenReturn(1);
        application.delete("role-3");
        verify(roleMapper).deleteById("role-3");
    }

    @Test
    void replacePermissionsMustDeduplicateAndAllowEmptySet() {
        when(roleMapper.selectById("role-1")).thenReturn(role("role-1", "A", true, 0L));
        PermissionEntity p1 = permission("permission-1", "a:read", true);
        PermissionEntity p2 = permission("permission-2", "b:read", true);
        when(permissionMapper.selectByIds(any())).thenReturn(List.of(p1, p2));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
            relation("role-1", "permission-1"), relation("role-1", "permission-2")
        ));

        var result = application.replacePermissions(
            "role-1", List.of("permission-1", "permission-1", "permission-2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<RolePermissionEntity>> batchCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(rolePermissionMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
        assertThat(result).extracting("id").containsExactly("permission-1", "permission-2");

        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
        assertThat(application.replacePermissions("role-1", List.of())).isEmpty();
        verify(rolePermissionMapper, times(1)).insert(anyCollection());
    }

    @Test
    void replacePermissionsMustRejectMissingOrDisabledPermissionBeforeChangingRelations() {
        when(roleMapper.selectById("role-1")).thenReturn(role("role-1", "A", true, 0L));
        when(permissionMapper.selectByIds(any())).thenReturn(List.of());
        assertError(() -> application.replacePermissions("role-1", List.of("missing")),
            AuthErrorCode.RESOURCE_NOT_FOUND);

        when(permissionMapper.selectByIds(any())).thenReturn(
            List.of(permission("permission-1", "a:read", false)));
        assertError(() -> application.replacePermissions("role-1", List.of("permission-1")),
            AuthErrorCode.PERMISSION_DISABLED);
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void queryPermissionsMustReturnStableCodeOrder() {
        when(roleMapper.selectById("role-1")).thenReturn(role("role-1", "A", true, 0L));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
            relation("role-1", "permission-2"), relation("role-1", "permission-1")
        ));
        when(permissionMapper.selectByIds(any())).thenReturn(List.of(
            permission("permission-2", "z:read", true),
            permission("permission-1", "a:read", true)
        ));

        assertThat(application.permissions("role-1")).extracting("code")
            .containsExactly("a:read", "z:read");
    }

    @Test
    void replacePermissionsMustRejectUnboundedSelection() {
        when(roleMapper.selectById("role-1")).thenReturn(role("role-1", "A", true, 0L));
        List<String> permissionIds = IntStream.range(0, 201)
            .mapToObj(index -> "permission-" + index)
            .toList();

        assertError(() -> application.replacePermissions("role-1", permissionIds),
            AuthErrorCode.RELATION_SELECTION_TOO_LARGE);
        verify(permissionMapper, never()).selectByIds(any());
    }

    private static RoleEntity role(String id, String code, boolean enabled, long version) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(code);
        entity.setEnabled(enabled);
        entity.setVersion(version);
        return entity;
    }

    private static PermissionEntity permission(String id, String code, boolean enabled) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(code);
        entity.setEnabled(enabled);
        entity.setVersion(0L);
        return entity;
    }

    private static RolePermissionEntity relation(String roleId, String permissionId) {
        RolePermissionEntity entity = new RolePermissionEntity();
        entity.setRoleId(roleId);
        entity.setPermissionId(permissionId);
        return entity;
    }

    private static void assertError(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(AuthException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
