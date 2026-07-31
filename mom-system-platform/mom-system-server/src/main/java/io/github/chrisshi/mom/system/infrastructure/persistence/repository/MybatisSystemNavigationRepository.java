package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationItem;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemNavigationItemEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemNavigationItemMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** MyBatis-Plus System Navigation Draft 单表 Adapter。 */
@Repository
public class MybatisSystemNavigationRepository
        extends CrudRepository<SystemNavigationItemMapper, SystemNavigationItemEntity>
        implements SystemNavigationRepository {

    @Override
    public Optional<SystemNavigationItem> findById(String id) {
        return Optional.ofNullable(getById(id)).map(MybatisSystemNavigationRepository::toDomain);
    }

    @Override
    public List<SystemNavigationItem> findByApplication(String applicationId) {
        return list(Wrappers.<SystemNavigationItemEntity>lambdaQuery()
                        .eq(SystemNavigationItemEntity::getApplicationId, applicationId)
                        .orderByAsc(SystemNavigationItemEntity::getClientChannel)
                        .orderByAsc(SystemNavigationItemEntity::getSortOrder)
                        .orderByAsc(SystemNavigationItemEntity::getRouteKey)
                        .orderByAsc(SystemNavigationItemEntity::getId))
                .stream().map(MybatisSystemNavigationRepository::toDomain).toList();
    }

    @Override
    public List<SystemNavigationItem> findByApplicationAndChannel(
            String applicationId, ClientChannel channel) {
        return list(Wrappers.<SystemNavigationItemEntity>lambdaQuery()
                        .eq(SystemNavigationItemEntity::getApplicationId, applicationId)
                        .eq(SystemNavigationItemEntity::getClientChannel, channel)
                        .orderByAsc(SystemNavigationItemEntity::getSortOrder)
                        .orderByAsc(SystemNavigationItemEntity::getRouteKey)
                        .orderByAsc(SystemNavigationItemEntity::getId))
                .stream().map(MybatisSystemNavigationRepository::toDomain).toList();
    }

    @Override
    public List<SystemNavigationItem> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<SystemNavigationItemEntity>lambdaQuery()
                        .in(SystemNavigationItemEntity::getId, ids))
                .stream().map(MybatisSystemNavigationRepository::toDomain).toList();
    }

    @Override
    public SystemNavigationItem insert(SystemNavigationItem item) {
        SystemNavigationItemEntity entity = toNewEntity(item);
        try {
            if (!save(entity)) {
                throw new IllegalStateException("Navigation 未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemCatalogException.Conflict(
                    "route_key_conflict", "同一 Application/Channel 的 routeKey 已存在", exception);
        }
        return findById(entity.getId()).orElseThrow(IllegalStateException::new);
    }

    @Override
    public boolean update(SystemNavigationItem item) {
        SystemNavigationItemEntity entity = baseUpdate(item);
        entity.setParentId(item.parentId());
        entity.setNavigationType(item.navigationType());
        entity.setI18nResourceCode(item.i18nResourceCode());
        entity.setI18nMessageKey(item.i18nMessageKey());
        entity.setPermissionCode(item.permissionCode());
        entity.setIconKey(item.iconKey());
        entity.setVisibleInMenu(item.visibleInMenu());
        entity.setVisibleInBreadcrumb(item.visibleInBreadcrumb());
        entity.setVisibleInTab(item.visibleInTab());
        entity.setKeepAlive(item.keepAlive());
        entity.setSortOrder(item.sortOrder());
        return updateById(entity);
    }

    @Override
    public boolean updateStatus(SystemNavigationItem item) {
        SystemNavigationItemEntity entity = baseUpdate(item);
        entity.setEnabled(item.enabled());
        return updateById(entity);
    }

    private static SystemNavigationItemEntity baseUpdate(SystemNavigationItem value) {
        SystemNavigationItemEntity entity = new SystemNavigationItemEntity();
        entity.setId(value.id());
        entity.setVersion(value.version());
        return entity;
    }

    private static SystemNavigationItemEntity toNewEntity(SystemNavigationItem value) {
        SystemNavigationItemEntity entity = new SystemNavigationItemEntity();
        entity.setApplicationId(value.applicationId());
        entity.setParentId(value.parentId());
        entity.setClientChannel(value.clientChannel());
        entity.setNavigationType(value.navigationType());
        entity.setRouteKey(value.routeKey());
        entity.setI18nResourceCode(value.i18nResourceCode());
        entity.setI18nMessageKey(value.i18nMessageKey());
        entity.setPermissionCode(value.permissionCode());
        entity.setIconKey(value.iconKey());
        entity.setVisibleInMenu(value.visibleInMenu());
        entity.setVisibleInBreadcrumb(value.visibleInBreadcrumb());
        entity.setVisibleInTab(value.visibleInTab());
        entity.setKeepAlive(value.keepAlive());
        entity.setSortOrder(value.sortOrder());
        entity.setEnabled(value.enabled());
        return entity;
    }

    private static SystemNavigationItem toDomain(SystemNavigationItemEntity entity) {
        return new SystemNavigationItem(entity.getId(), entity.getApplicationId(), entity.getParentId(),
                entity.getClientChannel(), entity.getNavigationType(), entity.getRouteKey(),
                entity.getI18nResourceCode(), entity.getI18nMessageKey(), entity.getPermissionCode(),
                entity.getIconKey(), Boolean.TRUE.equals(entity.getVisibleInMenu()),
                Boolean.TRUE.equals(entity.getVisibleInBreadcrumb()), Boolean.TRUE.equals(entity.getVisibleInTab()),
                Boolean.TRUE.equals(entity.getKeepAlive()), entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
