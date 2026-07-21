package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/** 外部 Party Binding 受控 Mapper。 */
@Mapper
public interface IamExternalUserBindingMapper extends MomBaseMapper<IamExternalUserBindingEntity> {
}
