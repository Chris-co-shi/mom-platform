package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserApplicationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/** 用户应用访问 Mapper，P1.5 仅管理 MOM_MOBILE_PDA。 */
@Mapper
public interface IamUserApplicationMapper extends MomBaseMapper<IamUserApplicationEntity> {

    /** @return 用户当前有效且启用的指定应用记录数 */
    @Select("""
            SELECT count(*) FROM iam_user_application
             WHERE user_id=#{userId} AND application_code=#{applicationCode} AND status='ENABLED'
               AND (valid_from IS NULL OR valid_from<=CURRENT_TIMESTAMP)
               AND (valid_until IS NULL OR valid_until>CURRENT_TIMESTAMP)
            """)
    int countEffective(@Param("userId") String userId,
            @Param("applicationCode") ApplicationCode applicationCode);

    /** 更新已存在的应用访问记录；不存在时由仓储插入新记录。 */
    @Update("""
            UPDATE iam_user_application
               SET status=#{status},valid_from=NULL,valid_until=NULL,
                   updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE user_id=#{userId} AND application_code=#{applicationCode}
            """)
    int updateAccess(@Param("userId") String userId,
            @Param("applicationCode") ApplicationCode applicationCode,
            @Param("status") IamRecordStatus status, @Param("now") Instant now,
            @Param("actor") String actor);
}
