package io.github.chrisshi.mom.qms.infrastructure.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** QMS 自有 Locale 文案。 */
@Getter @Setter @TableName("qms_i18n_translation")
public class QmsI18nTranslationEntity extends BaseEntity {
    private String messageId;
    private String localeCode;
    private String messageText;
}
