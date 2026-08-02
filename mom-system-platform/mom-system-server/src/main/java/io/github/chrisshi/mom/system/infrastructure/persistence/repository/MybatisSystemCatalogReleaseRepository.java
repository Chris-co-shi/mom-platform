package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemCatalogReleaseEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemCatalogReleaseMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Catalog 不可变 Release 的专用追加写与查询 Adapter。
 *
 * <p>该类型不继承通用 CrudRepository，避免向实现暴露 UPDATE/DELETE 作为正常能力。所有查询仍使用
 * MomBaseMapper 与 Lambda Wrapper，没有 XML、注解 SQL 或 JDBC。</p>
 */
@Repository
public class MybatisSystemCatalogReleaseRepository implements SystemCatalogReleaseRepository {
    private final SystemCatalogReleaseMapper mapper;

    public MybatisSystemCatalogReleaseRepository(SystemCatalogReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SystemCatalogRelease> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSystemCatalogReleaseRepository::toDomain);
    }

    @Override
    public Optional<SystemCatalogRelease> findByApplicationAndVersion(
            String applicationId, long releaseVersion) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SystemCatalogReleaseEntity>lambdaQuery()
                        .eq(SystemCatalogReleaseEntity::getApplicationId, applicationId)
                        .eq(SystemCatalogReleaseEntity::getReleaseVersion, releaseVersion)))
                .map(MybatisSystemCatalogReleaseRepository::toDomain);
    }

    @Override
    public List<SystemCatalogRelease> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<SystemCatalogReleaseEntity>lambdaQuery()
                        .in(SystemCatalogReleaseEntity::getId, ids))
                .stream().map(MybatisSystemCatalogReleaseRepository::toDomain).toList();
    }

    @Override
    public long nextVersion(String applicationId) {
        SystemCatalogReleaseEntity latest = mapper.selectOne(
                Wrappers.<SystemCatalogReleaseEntity>lambdaQuery()
                        .eq(SystemCatalogReleaseEntity::getApplicationId, applicationId)
                        .orderByDesc(SystemCatalogReleaseEntity::getReleaseVersion)
                        .orderByDesc(SystemCatalogReleaseEntity::getId)
                        .last("LIMIT 1"));
        return latest == null ? 1L : Math.addExact(latest.getReleaseVersion(), 1L);
    }

    @Override
    public SystemCatalogRelease insert(SystemCatalogRelease release) {
        SystemCatalogReleaseEntity entity = toNewEntity(release);
        try {
            if (mapper.insert(entity) != 1) {
                throw new IllegalStateException("Catalog Release 未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemCatalogException.Conflict(
                    "catalog_integrity_conflict", "Catalog 发布版本冲突", exception);
        }
        return findById(entity.getId()).orElseThrow(IllegalStateException::new);
    }

    @Override
    public ReleasePage findHistory(String applicationId, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        var filter = Wrappers.<SystemCatalogReleaseEntity>lambdaQuery()
                .eq(SystemCatalogReleaseEntity::getApplicationId, applicationId);
        long total = mapper.selectCount(filter);
        List<SystemCatalogRelease> items = total == 0 ? List.of() : mapper.selectList(
                        Wrappers.<SystemCatalogReleaseEntity>lambdaQuery()
                                .eq(SystemCatalogReleaseEntity::getApplicationId, applicationId)
                                .orderByDesc(SystemCatalogReleaseEntity::getReleaseVersion)
                                .orderByDesc(SystemCatalogReleaseEntity::getId)
                                .last("LIMIT " + size + " OFFSET " + offset))
                .stream().map(MybatisSystemCatalogReleaseRepository::toDomain).toList();
        return new ReleasePage(items, total, page, size);
    }

    private static SystemCatalogReleaseEntity toNewEntity(SystemCatalogRelease value) {
        SystemCatalogReleaseEntity entity = new SystemCatalogReleaseEntity();
        entity.setApplicationId(value.applicationId());
        entity.setApplicationCode(value.applicationCode());
        entity.setReleaseVersion(value.releaseVersion());
        entity.setSnapshotSchemaVersion(value.snapshotSchemaVersion());
        entity.setRouteContractVersion(value.routeContractVersion());
        entity.setSourceApplicationVersion(value.sourceApplicationVersion());
        entity.setSourceReleaseVersion(value.sourceReleaseVersion());
        entity.setSnapshotJson(value.snapshotJson());
        entity.setNodeCount(value.nodeCount());
        entity.setChecksum(value.checksum());
        entity.setChangeNote(value.changeNote());
        return entity;
    }

    private static SystemCatalogRelease toDomain(SystemCatalogReleaseEntity entity) {
        return new SystemCatalogRelease(entity.getId(), entity.getApplicationId(), entity.getApplicationCode(),
                entity.getReleaseVersion(), entity.getSnapshotSchemaVersion(), entity.getRouteContractVersion(),
                entity.getSourceApplicationVersion(), entity.getSourceReleaseVersion(), entity.getSnapshotJson(),
                entity.getNodeCount(), entity.getChecksum(), entity.getChangeNote(), entity.getCreatedBy(),
                entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
