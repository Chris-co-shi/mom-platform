package io.github.chrisshi.mom.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;

@TableName("auth_role")
/** Auth 角色持久化对象；角色只描述权限集合，不直接承载用户登录状态。 */
public class RoleEntity extends BaseEntity {

    /** 角色稳定编码，用于关系和权限配置引用。 */
    @TableField("code")
    private String code;

    /** 面向管理界面的角色名称。 */
    @TableField("name")
    private String name;

    /** 可选的角色说明。 */
    @TableField("description")
    private String description;

    /** 角色是否可继续用于授权配置。 */
    @TableField("enabled")
    private Boolean enabled;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
