package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.auth.application.model.PermissionView;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionPageParams;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionResourceEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.RolePermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionResourceMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.RolePermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.query.PermissionCatalogQueryMapper;
import io.github.chrisshi.mom.auth.infrastructure.query.PermissionCatalogRow;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Permission 目录管理与引用保护用例。
 *
 * <p>Permission 是 Resource 下的 Action，也是 RBAC 的最终业务授权单元。该 Application 从权威 Resource
 * 生成 code，负责本地引用、乐观锁和删除保护；不根据特殊 Role 绕过权限，也不承担登录认证。
 * 创建、启用与 Resource 删除通过资源行锁串行化，无物理 FK 下避免新增孤儿；数据库故障失败关闭。</p>
 */
@Component
public class PermissionApplication {
    private static final Pattern ACTION_CODE = Pattern.compile("[A-Z][A-Z0-9_-]*");

    private final PermissionMapper permissionMapper;
    private final PermissionResourceMapper resourceMapper;
    private final PermissionCatalogQueryMapper catalogQueryMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入 Permission、Role-Permission 持久化能力与统一分页适配器。
     *
     * @param permissionMapper Permission 单表 Mapper
     * @param resourceMapper 授权资源 Mapper
     * @param catalogQueryMapper 本地一对一 JOIN 分页 Mapper
     * @param rolePermissionMapper Role-Permission 关系 Mapper
     * @param pageAdapter 配置化分页适配器
     */
    public PermissionApplication(
        PermissionMapper permissionMapper, PermissionResourceMapper resourceMapper,
        PermissionCatalogQueryMapper catalogQueryMapper, RolePermissionMapper rolePermissionMapper,
        PageAdapter pageAdapter
    ) {
        this.permissionMapper = permissionMapper;
        this.resourceMapper = resourceMapper;
        this.catalogQueryMapper = catalogQueryMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.pageAdapter = pageAdapter;
    }

    /**
     * 创建 Permission。
     *
     * @param resourceId 权威 Resource 主键
     * @param actionCode 资源内动作编码
     * @param name 权限名称
     * @param description 可选描述
     * @param enabled 是否参与授权聚合
     * @return 新建 Permission 视图
     * @throws AuthException 权限编码冲突时抛出
     */
    @Transactional
    public PermissionView create(String resourceId, String actionCode, String name, String description, boolean enabled) {
        PermissionResourceEntity resource = requireResourceForWrite(resourceId);
        if (enabled && !Boolean.TRUE.equals(resource.getEnabled())) {
            throw new AuthException(AuthErrorCode.RESOURCE_DISABLED);
        }
        String normalizedAction = normalizeAction(actionCode);
        String normalizedCode = (resource.getDomainCode() + ":" + resource.getResourceCode() + ":" + normalizedAction)
            .toLowerCase(Locale.ROOT);
        if (normalizedCode.length() > 160) throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        ensureCodeAvailable(normalizedCode);
        PermissionEntity entity = new PermissionEntity();
        entity.setResourceId(resourceId);
        entity.setActionCode(normalizedAction);
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
        return PermissionView.from(entity, resource);
    }

    /**
     * 按主键查询 Permission。
     *
     * @param id Permission 主键
     * @return Permission 视图
     * @throws AuthException Permission 不存在时抛出
     */
    public PermissionView get(String id) {
        PermissionEntity entity = requirePermission(id);
        return PermissionView.from(entity, requireResource(entity.getResourceId()));
    }

    /**
     * 分页查询 Permission 目录。
     *
     * <p>本地 Permission→Resource 一对一 JOIN 先由 SQL 过滤再分页，避免前端当前页筛选和 N+1。
     * 统一使用 PageAdapter，排序固定为 code、id。</p>
     *
     * @param pageQuery 带强类型域、资源、关键字与状态过滤的分页请求
     * @return 平台统一分页结果
     */
    public PageResult<PermissionView> list(PageQuery<PermissionPageParams> pageQuery) {
        Page<PermissionCatalogRow> page = pageAdapter.toPage(pageQuery);
        PermissionPageParams params = pageQuery.params();
        catalogQueryMapper.searchCatalog(page, new PermissionPageParams(
            normalizeOptional(params.domainCode()), trimNullable(params.resourceId()),
            trimNullable(params.keyword()), params.enabled()
        ));
        return pageAdapter.toResult(page, PermissionView::from);
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
        if (enabled && !Boolean.TRUE.equals(entity.getEnabled())) {
            requireEnabledResource(entity.getResourceId());
        }
        entity.setEnabled(enabled);
        requireUpdateSucceeded(id, permissionMapper.updateById(entity));
        return PermissionView.from(entity, requireResource(entity.getResourceId()));
    }

    /**
     * 启用 Permission，使其可被新的角色权限关系分配并参与后续登录授权。
     *
     * <p>重复启用不执行无意义 UPDATE；状态变更不刷新已签发 Token 的 authority 快照。</p>
     *
     * @param id Permission 主键
     * @param version 客户端读取到的乐观锁版本
     * @return 启用后的 Permission 视图
     * @throws AuthException Permission 不存在或版本冲突时抛出
     */
    @Transactional
    public PermissionView enable(String id, long version) {
        return changeEnabled(id, true, version);
    }

    /**
     * 停用 Permission，阻止其被新的角色权限关系分配并从后续登录授权中排除。
     *
     * <p>V1 不回收或修改已签发 Token 中的 Permission 快照。</p>
     *
     * @param id Permission 主键
     * @param version 客户端读取到的乐观锁版本
     * @return 停用后的 Permission 视图
     * @throws AuthException Permission 不存在或版本冲突时抛出
     */
    @Transactional
    public PermissionView disable(String id, long version) {
        return changeEnabled(id, false, version);
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
        requireUpdateSucceeded(id, permissionMapper.deleteById(id));
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

    private PermissionView changeEnabled(String id, boolean enabled, long version) {
        PermissionEntity entity = requirePermission(id);
        requireVersion(entity.getVersion(), version);
        if (Boolean.valueOf(enabled).equals(entity.getEnabled())) {
            return PermissionView.from(entity, requireResource(entity.getResourceId()));
        }
        if (enabled) requireEnabledResource(entity.getResourceId());
        entity.setEnabled(enabled);
        requireUpdateSucceeded(id, permissionMapper.updateById(entity));
        return PermissionView.from(entity, requireResource(entity.getResourceId()));
    }

    private PermissionResourceEntity requireResource(String id) {
        PermissionResourceEntity resource = resourceMapper.selectById(id);
        if (resource == null) throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限所属资源不存在");
        return resource;
    }

    private PermissionResourceEntity requireResourceForWrite(String id) {
        PermissionResourceEntity resource = resourceMapper.selectOne(
            new LambdaQueryWrapper<PermissionResourceEntity>()
                .eq(PermissionResourceEntity::getId, id).last("FOR UPDATE")
        );
        if (resource == null) throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限所属资源不存在");
        return resource;
    }

    private void requireEnabledResource(String id) {
        if (!Boolean.TRUE.equals(requireResourceForWrite(id).getEnabled())) {
            throw new AuthException(AuthErrorCode.RESOURCE_DISABLED);
        }
    }

    private static String normalizeAction(String actionCode) {
        if (actionCode == null) throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        String normalized = actionCode.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > 60 || !ACTION_CODE.matcher(normalized).matches()) {
            throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private void requireUpdateSucceeded(String id, int affectedRows) {
        if (affectedRows == 1) {
            return;
        }
        if (permissionMapper.selectById(id) == null) {
            throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限不存在");
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
