package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamPermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** IAM Permission 目录 Mapper；目录仅由代码和 Flyway 管理，管理 API 只读。 */
@Mapper
public interface IamPermissionMapper extends MomBaseMapper<IamPermissionEntity> {

    /** @return 按 Permission 编码排序的管理分页投影 */
    List<IamAdminViews.PermissionView> selectAdminPermissions(
            @Param("domainCode") String domainCode, @Param("limit") int limit,
            @Param("offset") int offset);

    /** @return 输入集合中仍有效且启用的 Permission ID */
    List<String> selectEnabledIds(@Param("permissionIds") Collection<String> permissionIds);
}
