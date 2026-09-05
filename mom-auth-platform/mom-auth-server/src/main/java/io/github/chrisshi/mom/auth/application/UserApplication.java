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

/**
 * 用户管理与 User-Role 关系用例编排。
 *
 * <p>该类属于 Mini Auth 的 Application 层，负责用户生命周期、密码重置、引用保护、
 * User-Role 整体替换和事务边界。它可以直接依赖本模块 Mapper/Entity，但不承担 HTTP 协议、
 * 用户名密码认证或 Token 生命周期；这些职责分别属于 Controller 和 AuthenticationApplication。</p>
 *
 * <p>当前为 Level 1 分层，因此不为单表 CRUD 额外引入 Repository Port、ServiceImpl 或 Converter。</p>
 */
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

    /**
     * 创建本地登录用户。
     *
     * @param username 登录名；进入持久化前会去除首尾空白并转换为小写
     * @param password 明文密码；仅用于本次编码，不持久化明文
     * @param displayName 展示名称
     * @param enabled 是否允许登录
     * @return 新建用户视图
     * @throws AuthException 用户名重复时抛出冲突异常
     */
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
            // 前置唯一性检查只改善错误体验，数据库唯一约束才是并发场景下的最终防线。
            throw new AuthException(AuthErrorCode.USERNAME_CONFLICT, AuthErrorCode.USERNAME_CONFLICT.defaultMessage(), exception);
        }
        return UserView.from(entity);
    }

    /**
     * 按主键查询未逻辑删除用户。
     *
     * @param id 用户主键
     * @return 用户视图
     * @throws AuthException 用户不存在时抛出 RESOURCE_NOT_FOUND
     */
    public UserView get(String id) {
        return UserView.from(requireUser(id));
    }

    /**
     * 分页查询用户目录。
     *
     * <p>分页元数据统一由 {@link PageAdapter} 适配 MyBatis-Plus Page，Application 不自行计算
     * offset、totalPages 或复制 records。排序固定为 username、id，保证跨页结果稳定。</p>
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页数量
     * @return 平台统一分页结果
     */
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

    /**
     * 更新用户展示信息和启用状态。
     *
     * @param id 用户主键
     * @param displayName 展示名称
     * @param enabled 是否允许登录
     * @param version 客户端读取到的乐观锁版本
     * @return 更新后的用户视图
     * @throws AuthException 用户不存在或版本冲突时抛出
     */
    @Transactional
    public UserView update(String id, String displayName, boolean enabled, long version) {
        UserEntity entity = requireUser(id);
        requireVersion(entity.getVersion(), version);
        entity.setDisplayName(displayName.strip());
        entity.setEnabled(enabled);
        if (userMapper.updateById(entity) != 1) {
            // 即使读取时版本一致，也必须检查 UPDATE affected rows，覆盖并发写入窗口。
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return UserView.from(entity);
    }

    /**
     * 重置用户密码。
     *
     * <p>只持久化 PasswordEncoder 生成的摘要；该方法不负责登录认证，也不会刷新或撤销其他已签发 Token。</p>
     *
     * @param id 用户主键
     * @param newPassword 新明文密码
     * @param version 客户端读取到的乐观锁版本
     * @return 更新后的用户视图
     * @throws AuthException 用户不存在或版本冲突时抛出
     */
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

    /**
     * 删除用户。
     *
     * <p>Mini Auth 数据表不使用物理外键，因此删除前由 Application 显式检查 User-Role 引用，
     * 不做隐藏级联删除。</p>
     *
     * @param id 用户主键
     * @throws AuthException 用户不存在或仍被角色关系引用时抛出
     */
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

    /**
     * 查询用户当前分配的角色。
     *
     * @param userId 用户主键
     * @return 按 role code、id 稳定排序的角色列表
     * @throws AuthException 用户不存在时抛出
     */
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

    /**
     * 整体替换用户角色关系。
     *
     * <p>先校验所有目标角色存在，再在同一事务中删除旧关系并写入新关系；输入重复角色会被去重。
     * 该操作不负责刷新已经签发 Token 中的 authority 快照。</p>
     *
     * @param userId 用户主键
     * @param requestedRoleIds 目标角色主键集合
     * @return 替换后的角色列表
     * @throws AuthException 用户或任一目标角色不存在时抛出
     */
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
        if (actual == null || actual != expected) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }
}
