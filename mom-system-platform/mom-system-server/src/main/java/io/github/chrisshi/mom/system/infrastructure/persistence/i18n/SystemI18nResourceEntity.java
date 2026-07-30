package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Dynamic I18n Resource 的 MyBatis-Plus 行模型。
 *
 * <p>该类型只存在于 Infrastructure Persistence。稳定 applicationCode、resourceCode 和 defaultLocale
 * 创建后不允许更新；普通管理更新、Kill Switch 与发布指针推进统一使用 {@link Version} 乐观锁。
 * Resource 没有删除能力，因此只继承审计基类，不引入通用逻辑删除。</p>
 */
@Getter
@Setter
@TableName("system_i18n_resource")
public class SystemI18nResourceEntity extends BaseAuditEntity {
    @TableField("application_code")
    private String applicationCode;

    @TableField("resource_code")
    private String resourceCode;

    @TableField("resource_name")
    private String resourceName;

    @TableField("default_locale")
    private String defaultLocale;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("published_version")
    private Long publishedVersion;

    @TableField("published_by")
    private String publishedBy;

    @TableField("published_at")
    private Instant publishedAt;

    @Version
    @TableField("version")
    private Long version = 0L;

    /** 允许管理更新显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
