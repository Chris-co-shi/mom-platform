package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;

/** Refresh Token 摘要状态受控 Mapper。 */
@Mapper
public interface IamRefreshTokenMapper extends MomBaseMapper<IamRefreshTokenEntity> {
}
