package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import lombok.Getter;
import lombok.Setter;

/** 代码和 Flyway 管理的系统 Permission 目录实体，不作为 OAuth Scope。 */
@Getter
@Setter
@TableName("iam_permission")
public class IamPermissionEntity extends BaseEntity {
    /** domain:resource:action 格式的全局唯一编码。 */
    @TableField("code") private String code;
    /** Permission 中文名称。 */
    @TableField("name") private String name;
    /** 所属领域编码。 */
    @TableField("domain_code") private String domainCode;
    /** 受保护资源编码。 */
    @TableField("resource_code") private String resourceCode;
    /** 权限动作编码。 */
    @TableField("action_code") private String actionCode;
    /** 风险等级。 */
    @TableField("risk_level") private PermissionRiskLevel riskLevel;
    /** Permission 状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 权限用途与风险说明。 */
    @TableField("description") private String description;
    /** 是否为内置 Permission。 */
    @TableField("built_in") private Boolean builtIn;
}
