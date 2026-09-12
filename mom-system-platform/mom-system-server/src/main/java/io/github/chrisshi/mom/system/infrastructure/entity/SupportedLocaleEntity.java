package io.github.chrisshi.mom.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System SupportedLocale 的数据库行模型。
 *
 * <p>它是全平台 Locale Code 的权威，但不是翻译完成度或用户偏好。默认 Locale 必须启用且全表唯一，
 * Application 通过行锁串行化默认切换，数据库约束负责最终兜底。类型只属于 System Infrastructure，
 * 禁止其他服务依赖数据库 ID 或建立跨库外键。</p>
 */
@Getter
@Setter
@TableName("system_supported_locale")
public class SupportedLocaleEntity extends BaseEntity {
    @TableField("locale_code")
    private String localeCode;

    @TableField("display_name")
    private String displayName;

    @TableField("native_name")
    private String nativeName;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("is_default")
    private Boolean defaultLocale;

    @TableField("sort_order")
    private Integer sortOrder;
}
