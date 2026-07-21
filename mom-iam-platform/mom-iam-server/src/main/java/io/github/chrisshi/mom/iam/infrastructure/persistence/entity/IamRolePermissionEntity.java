package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseCreatedEntity;
import lombok.Getter;
import lombok.Setter;

/** 角色与 Permission 的纯创建关系实体，不包含 Deny、优先级或继承。 */
@Getter
@Setter
@TableName("iam_role_permission")
public class IamRolePermissionEntity extends BaseCreatedEntity {
    /** 角色 ID。 */
    @TableField("role_id") private String roleId;
    /** Permission ID。 */
    @TableField("permission_id") private String permissionId;
}
