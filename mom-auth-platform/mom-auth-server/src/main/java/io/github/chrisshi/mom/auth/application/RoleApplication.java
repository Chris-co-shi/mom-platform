package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色目录与 Role-Permission 关系用例编排。
 *
 * <p>该类维护角色生命周期、引用保护以及角色权限整体替换。它属于 Application 层，
 * 允许直接使用本模块 Mapper/Entity，但不承担 HTTP 返回协议，也不通过特殊角色硬编码绕过 Permission。</p>
 */
@Component
public class RoleApplication {

    private static final int MAX_PERMISSION_SELECTION = 200;

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

    /**
     * 创建角色。
     *
     * @param code 角色唯一编码
     * @param name 角色名称
     * @param description 可选描述
     * @param enabled 是否参与授权
     * @return 新建角色视图
     * @throws AuthException 角色编码冲突时抛出
     */
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
            // 数据库唯一约束负责并发场景下的最终一致性保护。
            throw new AuthException(AuthErrorCode.ROLE_CODE_CONFLICT, AuthErrorCode.ROLE_CODE_CONFLICT.defaultMessage(), exception);
        }
        return RoleView.from(entity);
    }

    /**
     * 按主键查询角色。
     *
     * @param id 角色主键
     * @return 角色视图
     * @throws AuthException 角色不存在时抛出
     */
    public RoleView get(String id) {
        return RoleView.from(requireRole(id));
    }

    /**
     * 分页查询角色目录。
     *
     * <p>统一使用 PageAdapter 适配分页，排序固定为 code、id，避免跨页顺序漂移。</p>
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页数量
     * @return 平台统一分页结果
     */
    public PageResult<RoleView> list(long pageNo, int pageSize) {
        Page<RoleEntity> page = PageAdapter.toPage(new PageQuery<>(null, pageNo, pageSize));
        roleMapper.selectPage(
            page,
            new LambdaQueryWrapper<RoleEntity>()
                .orderByAsc(RoleEntity::getCode)
                .orderByAsc(RoleEntity::getId)
        );
        return PageAdapter.toResult(page, RoleView::from);
    }

    /**
     * 更新角色基本信息和启用状态。
     *
     * @param id 角色主键
     * @param name 角色名称
     * @param description 可选描述
     * @param enabled 是否参与授权
     * @param version 客户端读取到的乐观锁版本
     * @return 更新后的角色视图
     * @throws AuthException 角色不存在或版本冲突时抛出
     */
    @Transactional
    public RoleView update(String id, String name, String description, boolean enabled, long version) {
        RoleEntity entity = requireRole(id);
        requireVersion(entity.getVersion(), version);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setEnabled(enabled);
        requireUpdateSucceeded(id, roleMapper.updateById(entity));
        return RoleView.from(entity);
    }

    /**
     * 启用角色，使其可被新的用户角色关系分配并参与后续登录授权。
     *
     * <p>重复启用不执行无意义 UPDATE；状态变更不刷新已签发 Token 的 authority 快照。</p>
     *
     * @param id 角色主键
     * @param version 客户端读取到的乐观锁版本
     * @return 启用后的角色视图
     * @throws AuthException 角色不存在或版本冲突时抛出
     */
    @Transactional
    public RoleView enable(String id, long version) {
        return changeEnabled(id, true, version);
    }

    /**
     * 停用角色，阻止其被新的用户角色关系分配并从后续登录授权中排除。
     *
     * <p>V1 不回收或修改已签发 Token 中的角色与 Permission 快照。</p>
     *
     * @param id 角色主键
     * @param version 客户端读取到的乐观锁版本
     * @return 停用后的角色视图
     * @throws AuthException 角色不存在或版本冲突时抛出
     */
    @Transactional
    public RoleView disable(String id, long version) {
        return changeEnabled(id, false, version);
    }

    /**
     * 删除角色。
     *
     * <p>由于业务关系表不建立物理外键，删除前显式检查 User-Role 与 Role-Permission 引用；
     * 任一引用存在时都拒绝删除，不做隐藏级联。</p>
     *
     * @param id 角色主键
     * @throws AuthException 角色不存在或仍被引用时抛出
     */
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
        requireUpdateSucceeded(id, roleMapper.deleteById(id));
    }

    /**
     * 查询角色当前拥有的 Permission。
     *
     * @param roleId 角色主键
     * @return 按 permission code、id 稳定排序的权限列表
     * @throws AuthException 角色不存在时抛出
     */
    public List<PermissionView> permissions(String roleId) {
        requireRole(roleId);
        List<String> permissionIds = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId)
        ).stream().map(RolePermissionEntity::getPermissionId).toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectByIds(permissionIds).stream()
            .sorted(java.util.Comparator.comparing(PermissionEntity::getCode).thenComparing(PermissionEntity::getId))
            .map(PermissionView::from)
            .toList();
    }

    /**
     * 整体替换角色的 Permission 关系。
     *
     * <p>先限制单次最多 200 个目标，再验证所有 Permission 存在且已启用，随后在同一事务中
     * 删除旧关系并写入新关系。该操作不会主动刷新既有 Token 中的 authority 快照。</p>
     *
     * @param roleId 角色主键
     * @param requestedPermissionIds 目标 Permission 主键集合
     * @return 替换后的 Permission 列表
     * @throws AuthException 角色/Permission 不存在、Permission 已停用或目标数量超限时抛出
     */
    @Transactional
    public List<PermissionView> replacePermissions(String roleId, List<String> requestedPermissionIds) {
        requireRole(roleId);
        if (requestedPermissionIds.size() > MAX_PERMISSION_SELECTION) {
            throw new AuthException(AuthErrorCode.RELATION_SELECTION_TOO_LARGE);
        }
        Set<String> permissionIds = new LinkedHashSet<>(requestedPermissionIds);
        validatePermissions(permissionIds);

        rolePermissionMapper.delete(
            new LambdaQueryWrapper<RolePermissionEntity>().eq(RolePermissionEntity::getRoleId, roleId)
        );
        List<RolePermissionEntity> relations = permissionIds.stream().map(permissionId -> {
            RolePermissionEntity relation = new RolePermissionEntity();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            return relation;
        }).toList();
        if (!relations.isEmpty()) {
            rolePermissionMapper.insert(relations);
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
        List<PermissionEntity> permissions = permissionMapper.selectByIds(permissionIds);
        Set<String> found = permissions.stream().map(PermissionEntity::getId).collect(java.util.stream.Collectors.toSet());
        for (String permissionId : permissionIds) {
            if (!found.contains(permissionId)) {
                throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限不存在: " + permissionId);
            }
        }
        if (permissions.stream().anyMatch(permission -> !Boolean.TRUE.equals(permission.getEnabled()))) {
            throw new AuthException(AuthErrorCode.PERMISSION_DISABLED);
        }
    }

    private RoleView changeEnabled(String id, boolean enabled, long version) {
        RoleEntity entity = requireRole(id);
        requireVersion(entity.getVersion(), version);
        if (Boolean.valueOf(enabled).equals(entity.getEnabled())) {
            return RoleView.from(entity);
        }
        entity.setEnabled(enabled);
        requireUpdateSucceeded(id, roleMapper.updateById(entity));
        return RoleView.from(entity);
    }

    private void requireUpdateSucceeded(String id, int affectedRows) {
        if (affectedRows == 1) {
            return;
        }
        if (roleMapper.selectById(id) == null) {
            throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
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
}
