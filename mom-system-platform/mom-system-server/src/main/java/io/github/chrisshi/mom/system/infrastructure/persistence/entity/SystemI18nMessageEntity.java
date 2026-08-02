package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Dynamic I18n Draft Message 的 MyBatis-Plus 行模型。
 *
 * <p>resourceId、messageKey 与 locale 创建后保持稳定；文本、启停状态和说明使用 BaseEntity 乐观锁更新。
 * 当前 Draft 没有删除 API，逻辑删除字段在正常业务路径保持 false。</p>
 */
@Getter
@Setter
@TableName("system_i18n_message")
public class SystemI18nMessageEntity extends BaseEntity {
    @TableField("resource_id")
    private String resourceId;

    @TableField("message_key")
    private String messageKey;

    @TableField("locale")
    private String locale;

    @TableField("message_value")
    private String messageValue;

    @TableField("enabled")
    private Boolean enabled;

    /** 允许管理更新显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
