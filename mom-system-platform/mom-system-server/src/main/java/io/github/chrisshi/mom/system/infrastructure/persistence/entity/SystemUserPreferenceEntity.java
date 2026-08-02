package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System 用户显示偏好的 MyBatis-Plus 行模型。
 *
 * <p>实体只继承创建/修改审计并显式声明 Version，不带逻辑删除。五个覆盖字段允许显式写 NULL；Reset
 * 保留行并清空覆盖。userId 只是 JWT sub 引用，不建立 IAM FK，数据库不可用时写入失败。</p>
 */
@Getter
@Setter
@TableName("system_user_preference")
public class SystemUserPreferenceEntity extends BaseAuditEntity {
    @TableField("user_id")
    private String userId;

    @TableField(value = "locale", updateStrategy = FieldStrategy.ALWAYS)
    private String locale;

    @TableField(value = "display_timezone", updateStrategy = FieldStrategy.ALWAYS)
    private String displayTimezone;

    @TableField(value = "theme_mode", updateStrategy = FieldStrategy.ALWAYS)
    private String themeMode;

    @TableField(value = "density", updateStrategy = FieldStrategy.ALWAYS)
    private String density;

    @TableField(value = "page_size", updateStrategy = FieldStrategy.ALWAYS)
    private Integer pageSize;

    @Version
    @TableField("version")
    private Long version = 0L;
}
