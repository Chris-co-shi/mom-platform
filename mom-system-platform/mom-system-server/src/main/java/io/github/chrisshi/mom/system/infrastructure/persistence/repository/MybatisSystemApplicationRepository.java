package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemApplicationEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemApplicationMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 复用 MyBatis-Plus CrudRepository 的 System Application 单表 Adapter。
 *
 * <p>通用 CRUD 只存在于 Infrastructure；上层只依赖领域 Port。数据库唯一冲突与 Version CAS 转换为稳定
 * Catalog 语义，数据库不可用时直接向上失败并回滚本地事务。</p>
 */
@Repository
public class MybatisSystemApplicationRepository
        extends CrudRepository<SystemApplicationMapper, SystemApplicationEntity>
        implements SystemApplicationRepository {

    @Override
    public Optional<SystemApplication> findById(String id) {
        return Optional.ofNullable(getById(id)).map(MybatisSystemApplicationRepository::toDomain);
    }

    @Override
    public Optional<SystemApplication> findByCode(String applicationCode) {
        return Optional.ofNullable(getOne(Wrappers.<SystemApplicationEntity>lambdaQuery()
                        .eq(SystemApplicationEntity::getApplicationCode, applicationCode)))
                .map(MybatisSystemApplicationRepository::toDomain);
    }

    @Override
    public SystemApplication insert(SystemApplication application) {
        SystemApplicationEntity entity = toNewEntity(application);
        try {
            if (!save(entity)) {
                throw new IllegalStateException("Application 未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemCatalogException.Conflict(
                    "application_code_conflict", "applicationCode 已存在", exception);
        }
        return findById(entity.getId()).orElseThrow(IllegalStateException::new);
    }

    @Override
    public boolean update(SystemApplication application) {
        SystemApplicationEntity entity = baseUpdate(application);
        entity.setApplicationType(application.applicationType());
        entity.setI18nResourceCode(application.i18nResourceCode());
        entity.setI18nMessageKey(application.i18nMessageKey());
        entity.setIconKey(application.iconKey());
        entity.setDescription(application.description());
        entity.setRouteContractVersion(application.routeContractVersion());
        entity.setSortOrder(application.sortOrder());
        return updateById(entity);
    }

    @Override
    public boolean updateStatus(SystemApplication application) {
        SystemApplicationEntity entity = baseUpdate(application);
        entity.setEnabled(application.enabled());
        return updateById(entity);
    }

    @Override
    public boolean touch(SystemApplication application) {
        SystemApplicationEntity entity = baseUpdate(application);
        entity.setSortOrder(application.sortOrder());
        return updateById(entity);
    }

    @Override
    public boolean updatePublished(SystemApplication application) {
        SystemApplicationEntity entity = baseUpdate(application);
        entity.setPublishedReleaseId(application.publishedReleaseId());
        entity.setPublishedVersion(application.publishedVersion());
        return updateById(entity);
    }

    @Override
    public ApplicationPage findPage(ApplicationQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        long total = count(filter(query));
        List<SystemApplication> items = total == 0 ? List.of() : list(filter(query)
                        .orderByAsc(SystemApplicationEntity::getSortOrder)
                        .orderByAsc(SystemApplicationEntity::getApplicationCode)
                        .orderByAsc(SystemApplicationEntity::getId)
                        .last("LIMIT " + query.size() + " OFFSET " + offset))
                .stream().map(MybatisSystemApplicationRepository::toDomain).toList();
        return new ApplicationPage(items, total, query.page(), query.size());
    }

    @Override
    public List<SystemApplication> findEnabledPublished() {
        return list(Wrappers.<SystemApplicationEntity>lambdaQuery()
                        .eq(SystemApplicationEntity::getEnabled, true)
                        .isNotNull(SystemApplicationEntity::getPublishedReleaseId)
                        .gt(SystemApplicationEntity::getPublishedVersion, 0)
                        .orderByAsc(SystemApplicationEntity::getSortOrder)
                        .orderByAsc(SystemApplicationEntity::getApplicationCode)
                        .orderByAsc(SystemApplicationEntity::getId))
                .stream().map(MybatisSystemApplicationRepository::toDomain).toList();
    }

    private static LambdaQueryWrapper<SystemApplicationEntity> filter(ApplicationQuery query) {
        return Wrappers.<SystemApplicationEntity>lambdaQuery()
                .eq(query.applicationCode() != null,
                        SystemApplicationEntity::getApplicationCode, query.applicationCode())
                .eq(query.applicationType() != null,
                        SystemApplicationEntity::getApplicationType, query.applicationType())
                .eq(query.enabled() != null, SystemApplicationEntity::getEnabled, query.enabled());
    }

    private static SystemApplicationEntity baseUpdate(SystemApplication value) {
        SystemApplicationEntity entity = new SystemApplicationEntity();
        entity.setId(value.id());
        entity.setVersion(value.version());
        return entity;
    }

    private static SystemApplicationEntity toNewEntity(SystemApplication value) {
        SystemApplicationEntity entity = new SystemApplicationEntity();
        entity.setApplicationCode(value.applicationCode());
        entity.setApplicationType(value.applicationType());
        entity.setI18nResourceCode(value.i18nResourceCode());
        entity.setI18nMessageKey(value.i18nMessageKey());
        entity.setIconKey(value.iconKey());
        entity.setDescription(value.description());
        entity.setRouteContractVersion(value.routeContractVersion());
        entity.setSortOrder(value.sortOrder());
        entity.setEnabled(value.enabled());
        entity.setPublishedVersion(0L);
        return entity;
    }

    private static SystemApplication toDomain(SystemApplicationEntity entity) {
        return new SystemApplication(entity.getId(), entity.getApplicationCode(), entity.getApplicationType(),
                entity.getI18nResourceCode(), entity.getI18nMessageKey(), entity.getIconKey(),
                entity.getDescription(), entity.getRouteContractVersion(), entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getPublishedReleaseId(),
                entity.getPublishedVersion(), entity.getVersion(), entity.getCreatedBy(), entity.getCreatedAt(),
                entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
