package io.github.chrisshi.mom.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseCreatedEntity;

@TableName("auth_role_permission")
public class RolePermissionEntity extends BaseCreatedEntity {

    @TableField("role_id")
    private String roleId;

    @TableField("permission_id")
    private String permissionId;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }
}
