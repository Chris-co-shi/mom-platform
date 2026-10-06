package io.github.chrisshi.mom.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;

/**
 * Auth Permission 持久化行，表示一个 Resource 下的一个 Action。
 *
 * <p>保留 code 作为最终 GrantedAuthority；resourceId/actionCode 由 Application 与 Resource 共同保证一致，
 * 不由客户端自由提供完整 code。乐观锁和逻辑删除沿用 BaseEntity，数据库故障失败关闭。</p>
 */
@TableName("auth_permission")
public class PermissionEntity extends BaseEntity {

    @TableField("resource_id")
    private String resourceId;

    @TableField("action_code")
    private String actionCode;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("enabled")
    private Boolean enabled;

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }

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
