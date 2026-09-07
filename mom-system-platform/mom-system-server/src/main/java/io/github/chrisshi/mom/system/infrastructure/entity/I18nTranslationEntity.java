package io.github.chrisshi.mom.system.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * System V1 动态 Translation 的数据库行模型。
 *
 * <p>每条记录由 Message ID 与 Locale Code 唯一定位，只保存普通文本和数字位置占位符。Message/Locale
 * 引用由同一事务内 Application 校验，不建立物理外键。更新使用 Version CAS；保存完成后由 Application
 * 请求 Framework 在事务提交后发送失效通知，SSE 失败不会反向修改本记录。</p>
 */
@Getter
@Setter
@TableName("system_i18n_translation")
public class I18nTranslationEntity extends BaseEntity {
    @TableField("message_id")
    private String messageId;

    @TableField("locale_code")
    private String localeCode;

    @TableField("message_text")
    private String messageText;
}
