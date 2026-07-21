package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import lombok.Getter;
import lombok.Setter;

/** IAM 角色目录实体；Role 表示职责，不绑定 Factory，不支持继承或 Deny。 */
@Getter
@Setter
@TableName("iam_role")
public class IamRoleEntity extends BaseEntity {
    /** 全局唯一角色编码。 */
    @TableField("code") private String code;
    /** 角色中文名称。 */
    @TableField("name") private String name;
    /** 角色适用用户类型。 */
    @TableField("applicable_user_type") private UserType applicableUserType;
    /** 角色状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 是否为 Flyway 初始化的内置角色。 */
    @TableField("built_in") private Boolean builtIn;
    /** 角色职责与管理边界说明。 */
    @TableField("description") private String description;
}
