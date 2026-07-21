package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** INTERNAL 用户最小内部身份扩展资料；不复制组织、部门、岗位或工厂主数据。 */
@Getter
@Setter
@TableName("iam_internal_user_profile")
public class IamInternalUserProfileEntity extends BaseAuditEntity {
    /** 内部用户 ID，一个用户最多一条资料。 */
    @TableField("user_id") private String userId;
    /** 可选员工编号，非空值全局唯一。 */
    @TableField("employee_no") private String employeeNo;
    /** 乐观锁版本号。 */
    @Version @TableField("version") private Long version = 0L;
}
