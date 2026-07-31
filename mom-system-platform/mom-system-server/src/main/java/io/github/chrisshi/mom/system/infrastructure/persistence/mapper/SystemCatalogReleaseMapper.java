package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemCatalogReleaseEntity;
import org.apache.ibatis.annotations.Mapper;

/** Catalog 不可变 Release Mapper；只由专用追加写 Adapter 使用。 */
@Mapper
public interface SystemCatalogReleaseMapper extends MomBaseMapper<SystemCatalogReleaseEntity> {
}
