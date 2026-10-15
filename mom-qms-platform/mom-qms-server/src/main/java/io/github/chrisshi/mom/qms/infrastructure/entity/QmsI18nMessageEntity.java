package io.github.chrisshi.mom.qms.infrastructure.entity;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** QMS 自有动态文案定义。 */
@Getter @Setter @TableName("qms_i18n_message_definition")
public class QmsI18nMessageEntity extends BaseEntity {
    private String messageKey;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String description;
    private Boolean enabled;
}
