package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/** 外部用户唯一 Party Binding Mapper。 */
@Mapper
public interface IamExternalUserBindingMapper extends MomBaseMapper<IamExternalUserBindingEntity> {

    /** @return 用户当前绑定记录，不按状态过滤 */
    @Select("SELECT * FROM iam_external_user_binding WHERE user_id=#{userId}")
    IamExternalUserBindingEntity selectByUserId(@Param("userId") String userId);

    /** @return 已考虑状态与有效期的唯一 Party Binding */
    IamExternalUserBindingEntity selectEffectiveByUserId(
            @Param("userId") String userId, @Param("now") Instant now);

    /** 重绑已存在的外部主体记录；不存在时由仓储插入。 */
    @Update("""
            UPDATE iam_external_user_binding
               SET party_type=#{partyType},party_id=#{partyId},status='ENABLED',
                   valid_from=#{now},valid_until=NULL,updated_at=#{now},
                   updated_by=#{actor},version=version+1
             WHERE user_id=#{userId}
            """)
    int rebind(@Param("userId") String userId, @Param("partyType") PartyType partyType,
            @Param("partyId") String partyId, @Param("now") Instant now,
            @Param("actor") String actor);
}
