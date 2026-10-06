package io.github.chrisshi.mom.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;

/**
 * Auth 权限目录的业务授权资源持久化行。
 *
 * <p>只表达资源元数据和本地启停状态，不是菜单、路由或运行时 authority。Application 负责引用保护，
 * 本 Entity 不跨 Controller 边界；乐观锁及审计由 BaseEntity 统一处理，数据库不可用时写入失败关闭。</p>
 */
@TableName("auth_permission_resource")
public class PermissionResourceEntity extends BaseEntity {
    @TableField("domain_code")
    private String domainCode;
    @TableField("resource_code")
    private String resourceCode;
    @TableField("name")
    private String name;
    @TableField("description")
    private String description;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("enabled")
    private Boolean enabled;

    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getResourceCode() { return resourceCode; }
    public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
