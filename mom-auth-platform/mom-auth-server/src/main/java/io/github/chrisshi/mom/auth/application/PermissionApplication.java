package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.auth.application.model.PermissionView;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RolePermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RolePermissionMapper;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permission 目录管理与引用保护用例。
 *
 * <p>Permission 是 RBAC 的最终业务授权单元。该 Application 负责目录 CRUD、乐观锁和删除前引用检查；
 * 它不根据特殊 Role 做权限绕过，也不承担 Spring Security Authentication 的生成。</p>
 */
@Component
public class PermissionApplication {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public PermissionApplication(PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * 创建 Permission。
     *
     * @param code 权限唯一编码，例如 auth:user:read
     * @param name 权限名称
     * @param description 可选描述
     * @param enabled 是否参与授权聚合
     * @return 新建 Permission 视图
     * @throws AuthException 权限编码冲突时抛出
     */
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
        return PermissionView.from(entity);
    }

    /**
     * 按主键查询 Permission。
     *
     * @param id Permission 主键
     * @return Permission 视图
     * @throws AuthException Permission 不存在时抛出
     */
    public PermissionView get(String id) {
        return PermissionView.from(requirePermission(id));
    }

    /**
     * 分页查询 Permission 目录。
     *
     * <p>统一使用 PageAdapter 适配分页，排序固定为 code、id。</p>
     *
     * @param pageNo 从 1 开始的页码
     * @param pageSize 每页数量
     * @return 平台统一分页结果
     */
    public PageResult<PermissionView> list(long pageNo, int pageSize) {
        Page<PermissionEntity> page = PageAdapter.toPage(new PageQuery<>(null, pageNo, pageSize));
        permissionMapper.selectPage(
            page,
            new LambdaQueryWrapper<PermissionEntity>()
                .orderByAsc(PermissionEntity::getCode)
                .orderByAsc(PermissionEntity::getId)
        );
        return PageAdapter.toResult(page, PermissionView::from);
    }

    /**
     * 更新 Permission 基本信息和启用状态。
     *
     * @param id Permission 主键
     * @param name 权限名称
     * @param description 可选描述
     * @param enabled 是否参与授权聚合
     * @param version 客户端读取到的乐观锁版本
     * @return 更新后的 Permission 视图
     * @throws AuthException Permission 不存在或版本冲突时抛出
     */
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
        return PermissionView.from(entity);
    }

    /**
     * 删除 Permission。
     *
     * <p>Role-Permission 不使用物理外键，因此删除前显式检查引用，存在关系时拒绝删除。</p>
     *
     * @param id Permission 主键
     * @throws AuthException Permission 不存在或仍被角色引用时抛出
     */
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
}
