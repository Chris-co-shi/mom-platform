package io.github.chrisshi.mom.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System V1 动态 I18n 消息定义的数据库行模型。
 *
 * <p>消息只允许位于 {@code system} 或 {@code system.*} namespace，{@code messageKey} 创建后不可修改。
 * Entity 不保存 Locale 文案，Translation 由独立表维护。禁用是 V1 唯一退役方式；无物理删除 API。
 * 数据库不可用时 Runtime 读取失败关闭，不回退到中央远程服务。</p>
 */
@Getter
@Setter
@TableName("system_i18n_message_definition")
public class I18nMessageEntity extends BaseEntity {
    @TableField("namespace")
    private String namespace;

    @TableField("message_key")
    private String messageKey;

    /** 可选语义说明允许显式清空。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    @TableField("enabled")
    private Boolean enabled;
}
