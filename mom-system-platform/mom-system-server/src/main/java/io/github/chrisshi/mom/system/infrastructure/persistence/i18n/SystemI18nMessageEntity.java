package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Dynamic I18n Draft Message 的 MyBatis-Plus 行模型。
 *
 * <p>resourceId、messageKey 与 locale 创建后保持稳定；文本、启停状态和说明使用乐观锁更新。Draft 没有
 * 删除 API，且修改只影响下一次显式发布，不会回写已经生成的不可变 Release。</p>
 */
@Getter
@Setter
@TableName("system_i18n_message")
public class SystemI18nMessageEntity extends BaseAuditEntity {
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

    @Version
    @TableField("version")
    private Long version = 0L;

    /** 允许管理更新显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
