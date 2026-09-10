package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.LocationTypeEntity;
import org.apache.ibatis.annotations.Mapper;

/** LocationType 单表 Mapper；动态类型 Code 不被解释为 Java 枚举或业务 Capability。 */
@Mapper
public interface LocationTypeMapper extends MomBaseMapper<LocationTypeEntity> {
}
