package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserRoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RoleMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

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

class UserApplicationTest {

    private UserMapper userMapper;
    private UserRoleMapper userRoleMapper;
    private RoleMapper roleMapper;
    private PasswordEncoder passwordEncoder;
    private UserApplication application;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        roleMapper = mock(RoleMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        application = new UserApplication(userMapper, userRoleMapper, roleMapper, passwordEncoder);
    }

    @Test
    void createMustNormalizeUsernameAndEncodePassword() {
        when(passwordEncoder.encode("Password@123")).thenReturn("{bcrypt}encoded");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId("user-1");
            return 1;
        });

        var result = application.create(" Admin ", "Password@123", " 管理员 ", true);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("{bcrypt}encoded");
        assertThat(result.username()).isEqualTo("admin");
        assertThat(result.displayName()).isEqualTo("管理员");
    }

    @Test
    void createMustMapPrecheckAndConcurrentUsernameConflicts() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.create("admin", "Password@123", "Admin", true),
            AuthErrorCode.USERNAME_CONFLICT);
        verify(passwordEncoder, never()).encode(any());

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}encoded");
        doThrow(new DuplicateKeyException("uk_auth_user_username"))
            .when(userMapper).insert(any(UserEntity.class));
        assertError(() -> application.create("admin", "Password@123", "Admin", true),
            AuthErrorCode.USERNAME_CONFLICT);
    }

    @Test
    void updateAndResetPasswordMustUseOptimisticLockAndKeepPasswordSeparate() {
        UserEntity user = user("user-1", true, 3L);
        when(userMapper.selectById("user-1")).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);
        when(passwordEncoder.encode("NewPassword@123")).thenReturn("{bcrypt}new");

        var updated = application.update("user-1", " New Name ", false, 3L);
        assertThat(updated.displayName()).isEqualTo("New Name");
        assertThat(updated.enabled()).isFalse();

        var reset = application.resetPassword("user-1", "NewPassword@123", 3L);
        assertThat(reset.id()).isEqualTo("user-1");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
    }

    @Test
    void affectedRowsZeroMustDistinguishMissingResourceFromVersionConflict() {
        UserEntity user = user("user-1", true, 2L);
        when(userMapper.selectById("user-1")).thenReturn(user, user);
        when(userMapper.updateById(user)).thenReturn(0);
        assertError(() -> application.update("user-1", "Name", true, 2L),
            AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);

        when(userMapper.selectById("user-2")).thenReturn(user("user-2", true, 1L), null);
        when(userMapper.updateById(any(UserEntity.class))).thenReturn(0);
        assertError(() -> application.update("user-2", "Name", true, 1L),
            AuthErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void enableAndDisableMustBeExplicitAndRepeatedStateMustNotWrite() {
        UserEntity enabled = user("user-1", true, 0L);
        when(userMapper.selectById("user-1")).thenReturn(enabled);
        assertThat(application.enable("user-1", 0L).enabled()).isTrue();
        verify(userMapper, never()).updateById(enabled);

        UserEntity disabled = user("user-2", false, 0L);
        when(userMapper.selectById("user-2")).thenReturn(disabled);
        when(userMapper.updateById(disabled)).thenReturn(1);
        assertThat(application.enable("user-2", 0L).enabled()).isTrue();

        UserEntity active = user("user-3", true, 0L);
        when(userMapper.selectById("user-3")).thenReturn(active);
        when(userMapper.updateById(active)).thenReturn(1);
        assertThat(application.disable("user-3", 0L).enabled()).isFalse();
    }

    @Test
    void deleteMustRejectReferencedUserAndDeleteUnreferencedUser() {
        when(userMapper.selectById("referenced")).thenReturn(user("referenced", true, 0L));
        when(userRoleMapper.selectCount(any())).thenReturn(1L);
        assertError(() -> application.delete("referenced"), AuthErrorCode.RESOURCE_REFERENCED);
        verify(userMapper, never()).deleteById("referenced");

        when(userMapper.selectById("free")).thenReturn(user("free", true, 0L));
        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.deleteById("free")).thenReturn(1);
        application.delete("free");
        verify(userMapper).deleteById("free");
    }

    @Test
    void replaceRolesMustDeduplicateAndAllowEmptySet() {
        when(userMapper.selectById("user-1")).thenReturn(user("user-1", true, 0L));
        RoleEntity role1 = role("role-1", "A", true);
        RoleEntity role2 = role("role-2", "B", true);
        when(roleMapper.selectByIds(any())).thenReturn(List.of(role1, role2));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
            relation("user-1", "role-1"), relation("user-1", "role-2")
        ));

        var result = application.replaceRoles("user-1", List.of("role-1", "role-1", "role-2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserRoleEntity>> batchCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(userRoleMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
        assertThat(result).extracting("id").containsExactly("role-1", "role-2");

        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        assertThat(application.replaceRoles("user-1", List.of())).isEmpty();
        verify(userRoleMapper, times(1)).insert(anyCollection());
    }

    @Test
    void replaceRolesMustRejectMissingOrDisabledRoleBeforeChangingRelations() {
        when(userMapper.selectById("user-1")).thenReturn(user("user-1", true, 0L));
        when(roleMapper.selectByIds(any())).thenReturn(List.of());
        assertError(() -> application.replaceRoles("user-1", List.of("missing")),
            AuthErrorCode.RESOURCE_NOT_FOUND);

        when(roleMapper.selectByIds(any())).thenReturn(List.of(role("role-1", "A", false)));
        assertError(() -> application.replaceRoles("user-1", List.of("role-1")),
            AuthErrorCode.ROLE_DISABLED);
        verify(userRoleMapper, never()).delete(any());
    }

    @Test
    void replaceRolesMustRejectUnboundedSelection() {
        when(userMapper.selectById("user-1")).thenReturn(user("user-1", true, 0L));
        List<String> roleIds = IntStream.range(0, 201).mapToObj(index -> "role-" + index).toList();

        assertError(() -> application.replaceRoles("user-1", roleIds),
            AuthErrorCode.RELATION_SELECTION_TOO_LARGE);
        verify(roleMapper, never()).selectByIds(any());
    }

    private static UserEntity user(String id, boolean enabled, long version) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername(id);
        entity.setPasswordHash("{bcrypt}hash");
        entity.setDisplayName(id);
        entity.setEnabled(enabled);
        entity.setVersion(version);
        return entity;
    }

    private static RoleEntity role(String id, String code, boolean enabled) {
        RoleEntity entity = new RoleEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(code);
        entity.setEnabled(enabled);
        entity.setVersion(0L);
        return entity;
    }

    private static UserRoleEntity relation(String userId, String roleId) {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        return entity;
    }

    private static void assertError(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(AuthException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
