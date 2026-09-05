package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.auth.application.model.RoleView;
import io.github.chrisshi.mom.auth.application.model.UserView;
import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserRoleEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RoleMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserRoleMapper;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 用户管理及 User-Role 关系编排。 */
@Component
public class UserApplication {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserApplication(
        UserMapper userMapper,
        UserRoleMapper userRoleMapper,
        RoleMapper roleMapper,
        PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserView create(String username, String password, String displayName, boolean enabled) {
        String normalizedUsername = normalizeUsername(username);
        ensureUsernameAvailable(normalizedUsername);

        UserEntity entity = new UserEntity();
        entity.setUsername(normalizedUsername);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setDisplayName(displayName.strip());
        entity.setEnabled(enabled);

        try {
            userMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new AuthException(AuthErrorCode.USERNAME_CONFLICT, AuthErrorCode.USERNAME_CONFLICT.defaultMessage(), exception);
        }
        return UserView.from(entity);
    }

    public UserView get(String id) {
        return UserView.from(requireUser(id));
    }

    public PageResult<UserView> list(long pageNo, int pageSize) {
        Page<UserEntity> page = PageAdapter.toPage(new PageQuery<>(null, pageNo, pageSize));
        userMapper.selectPage(
            page,
            new LambdaQueryWrapper<UserEntity>()
                .orderByAsc(UserEntity::getUsername)
                .orderByAsc(UserEntity::getId)
        );
        return PageAdapter.toResult(page, UserView::from);
    }

    @Transactional
    public UserView update(String id, String displayName, boolean enabled, long version) {
        UserEntity entity = requireUser(id);
        requireVersion(entity.getVersion(), version);
        entity.setDisplayName(displayName.strip());
        entity.setEnabled(enabled);
        if (userMapper.updateById(entity) != 1) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return UserView.from(entity);
    }

    @Transactional
    public UserView resetPassword(String id, String newPassword, long version) {
        UserEntity entity = requireUser(id);
        requireVersion(entity.getVersion(), version);
        entity.setPasswordHash(passwordEncoder.encode(newPassword));
        if (userMapper.updateById(entity) != 1) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return UserView.from(entity);
    }

    @Transactional
    public void delete(String id) {
        requireUser(id);
        long references = userRoleMapper.selectCount(
            new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, id)
        );
        if (references > 0) {
            throw new AuthException(AuthErrorCode.RESOURCE_REFERENCED, "用户仍分配有角色，请先解除角色关系");
        }
        userMapper.deleteById(id);
    }

    public List<RoleView> roles(String userId) {
        requireUser(userId);
        List<String> roleIds = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId)
        ).stream().map(UserRoleEntity::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectByIds(roleIds).stream()
            .sorted(java.util.Comparator.comparing(RoleEntity::getCode).thenComparing(RoleEntity::getId))
            .map(RoleView::from)
            .toList();
    }

    @Transactional
    public List<RoleView> replaceRoles(String userId, List<String> requestedRoleIds) {
        requireUser(userId);
        Set<String> roleIds = new LinkedHashSet<>(requestedRoleIds);
        validateRoles(roleIds);

        userRoleMapper.delete(
            new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId)
        );
        for (String roleId : roleIds) {
            UserRoleEntity relation = new UserRoleEntity();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
        return roles(userId);
    }

    private UserEntity requireUser(String id) {
        UserEntity entity = userMapper.selectById(id);
        if (entity == null) {
            throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return entity;
    }

    private void ensureUsernameAvailable(String username) {
        long count = userMapper.selectCount(
            new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username)
        );
        if (count > 0) {
            throw new AuthException(AuthErrorCode.USERNAME_CONFLICT);
        }
    }

    private void validateRoles(Set<String> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<RoleEntity> roles = roleMapper.selectByIds(roleIds);
        Set<String> found = roles.stream().map(RoleEntity::getId).collect(java.util.stream.Collectors.toSet());
        for (String roleId : roleIds) {
            if (!found.contains(roleId)) {
                throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "角色不存在: " + roleId);
            }
        }
    }

    private static String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private static void requireVersion(Long actual, long expected) {
        if (actual == null || actual.longValue() != expected) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }
}
