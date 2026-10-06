package io.github.chrisshi.mom.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionResourcePageParams;
import io.github.chrisshi.mom.auth.application.model.PermissionResourceView;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionResourceEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionResourceMapper;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Auth 授权资源目录的 Level 1 用例。
 *
 * <p>资源只组织 Permission Catalog，不参与 Token authority 计算。Application 直接协调本模块 Mapper，
 * 保证唯一性、乐观版本和删除引用保护；本地写事务中数据库故障会回滚并向上失败关闭。
 * 停用资源不级联改写 Permission 或角色关系，也不回收既有 Token。</p>
 */
@Component
public class PermissionResourceApplication {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_-]*");
    private final PermissionResourceMapper resourceMapper;
    private final PermissionMapper permissionMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入 Auth 本地 Mapper 和统一分页适配器；不产生数据库副作用。
     *
     * @param resourceMapper 资源 Mapper
     * @param permissionMapper 权限 Mapper，用于删除引用保护
     * @param pageAdapter 统一分页适配器
     */
    public PermissionResourceApplication(
        PermissionResourceMapper resourceMapper, PermissionMapper permissionMapper, PageAdapter pageAdapter
    ) {
        this.resourceMapper = resourceMapper;
        this.permissionMapper = permissionMapper;
        this.pageAdapter = pageAdapter;
    }

    /**
     * 创建稳定编码的资源；重复域/资源组合返回 409，不自动重试。
     *
     * @param domainCode 授权域编码
     * @param resourceCode 域内资源编码
     * @param name 展示名称
     * @param description 可选说明
     * @param sortOrder 非负排序值
     * @param enabled 本地状态
     * @return 新建资源视图
     */
    @Transactional
    public PermissionResourceView create(
        String domainCode, String resourceCode, String name, String description, int sortOrder, boolean enabled
    ) {
        String domain = normalizeCode(domainCode, 32);
        String resource = normalizeCode(resourceCode, 64);
        if (sortOrder < 0) throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        if (resourceMapper.selectCount(new LambdaQueryWrapper<PermissionResourceEntity>()
            .eq(PermissionResourceEntity::getDomainCode, domain)
            .eq(PermissionResourceEntity::getResourceCode, resource)) > 0) {
            throw new AuthException(AuthErrorCode.RESOURCE_CODE_CONFLICT);
        }
        PermissionResourceEntity entity = new PermissionResourceEntity();
        entity.setDomainCode(domain);
        entity.setResourceCode(resource);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setSortOrder(sortOrder);
        entity.setEnabled(enabled);
        try {
            resourceMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new AuthException(AuthErrorCode.RESOURCE_CODE_CONFLICT,
                AuthErrorCode.RESOURCE_CODE_CONFLICT.defaultMessage(), exception);
        }
        return PermissionResourceView.from(entity);
    }

    /**
     * 查询单个资源；不存在时返回稳定 404。
     *
     * @param id 资源技术主键
     * @return 资源视图
     */
    public PermissionResourceView get(String id) {
        return PermissionResourceView.from(requireResource(id));
    }

    /**
     * 按 domain、关键字与启停状态进行服务端分页；稳定按排序值、编码和 ID 排序。
     *
     * @param query 强类型分页请求
     * @return 资源页
     */
    public PageResult<PermissionResourceView> list(PageQuery<PermissionResourcePageParams> query) {
        Page<PermissionResourceEntity> page = pageAdapter.toPage(query);
        PermissionResourcePageParams params = query.params();
        String keyword = trimNullable(params.keyword());
        resourceMapper.selectPage(page, new LambdaQueryWrapper<PermissionResourceEntity>()
            .eq(trimNullable(params.domainCode()) != null, PermissionResourceEntity::getDomainCode,
                normalizeOptional(params.domainCode()))
            .eq(params.enabled() != null, PermissionResourceEntity::getEnabled, params.enabled())
            .and(keyword != null, wrapper -> wrapper
                .like(PermissionResourceEntity::getResourceCode, keyword)
                .or().like(PermissionResourceEntity::getName, keyword)
                .or().like(PermissionResourceEntity::getDescription, keyword))
            .orderByAsc(PermissionResourceEntity::getSortOrder)
            .orderByAsc(PermissionResourceEntity::getDomainCode)
            .orderByAsc(PermissionResourceEntity::getResourceCode)
            .orderByAsc(PermissionResourceEntity::getId));
        return pageAdapter.toResult(page, PermissionResourceView::from);
    }

    /**
     * 更新展示字段和本地状态，不允许重命名稳定编码；使用乐观版本防覆盖。
     *
     * @param id 资源主键
     * @param name 展示名称
     * @param description 可选说明
     * @param sortOrder 非负排序值
     * @param enabled 本地状态
     * @param version 读取时版本
     * @return 最新资源视图
     */
    @Transactional
    public PermissionResourceView update(
        String id, String name, String description, int sortOrder, boolean enabled, long version
    ) {
        PermissionResourceEntity entity = requireResourceForWrite(id);
        requireVersion(entity, version);
        if (sortOrder < 0) throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        entity.setName(name.strip());
        entity.setDescription(trimNullable(description));
        entity.setSortOrder(sortOrder);
        entity.setEnabled(enabled);
        requireUpdateSucceeded(id, resourceMapper.updateById(entity));
        return PermissionResourceView.from(entity);
    }

    /**
     * 启用资源；不级联启用子权限。
     *
     * @param id 资源主键
     * @param version 读取时版本
     * @return 最新资源视图
     */
    @Transactional
    public PermissionResourceView enable(String id, long version) {
        return changeEnabled(id, true, version);
    }

    /**
     * 停用资源；只阻止后续创建/启用子权限，不改动既有授权快照。
     *
     * @param id 资源主键
     * @param version 读取时版本
     * @return 最新资源视图
     */
    @Transactional
    public PermissionResourceView disable(String id, long version) {
        return changeEnabled(id, false, version);
    }

    /**
     * 删除无 Permission 引用的资源；无物理 FK，故显式引用检查且不级联删除。
     *
     * @param id 资源主键
     */
    @Transactional
    public void delete(String id) {
        requireResourceForWrite(id);
        if (permissionMapper.selectCount(new LambdaQueryWrapper<PermissionEntity>()
            .eq(PermissionEntity::getResourceId, id)) > 0) {
            throw new AuthException(AuthErrorCode.RESOURCE_REFERENCED, "资源下仍有权限，不能删除");
        }
        requireUpdateSucceeded(id, resourceMapper.deleteById(id));
    }

    private PermissionResourceView changeEnabled(String id, boolean enabled, long version) {
        PermissionResourceEntity entity = requireResourceForWrite(id);
        requireVersion(entity, version);
        if (Boolean.valueOf(enabled).equals(entity.getEnabled())) return PermissionResourceView.from(entity);
        entity.setEnabled(enabled);
        requireUpdateSucceeded(id, resourceMapper.updateById(entity));
        return PermissionResourceView.from(entity);
    }

    private PermissionResourceEntity requireResource(String id) {
        PermissionResourceEntity entity = resourceMapper.selectById(id);
        if (entity == null) throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限资源不存在");
        return entity;
    }

    private PermissionResourceEntity requireResourceForWrite(String id) {
        PermissionResourceEntity entity = resourceMapper.selectOne(
            new LambdaQueryWrapper<PermissionResourceEntity>()
                .eq(PermissionResourceEntity::getId, id).last("FOR UPDATE")
        );
        if (entity == null) throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND, "权限资源不存在");
        return entity;
    }

    private void requireUpdateSucceeded(String id, int affected) {
        if (affected == 1) return;
        if (resourceMapper.selectById(id) == null) throw new AuthException(AuthErrorCode.RESOURCE_NOT_FOUND);
        throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
    }

    private static void requireVersion(PermissionResourceEntity entity, long version) {
        if (entity.getVersion() == null || entity.getVersion() != version) {
            throw new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private static String normalizeCode(String value, int maxLength) {
        if (value == null) throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength || !CODE.matcher(normalized).matches()) {
            throw new AuthException(AuthErrorCode.RESOURCE_INVALID_CODE);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : normalizeCode(value, 32);
    }

    private static String trimNullable(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
