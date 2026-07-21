package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamOauthClientPolicyEntity;
import org.apache.ibatis.annotations.Mapper;

/** MOM OAuth Client Policy 受控 Mapper。 */
@Mapper
public interface IamOauthClientPolicyMapper extends MomBaseMapper<IamOauthClientPolicyEntity> {
}
