package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserApplicationEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户级应用访问受控 Mapper。 */
@Mapper
public interface IamUserApplicationMapper extends MomBaseMapper<IamUserApplicationEntity> {
}
