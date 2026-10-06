package io.github.chrisshi.mom.i18n.model;

import java.time.Instant;

/**
 * Framework 可理解的单语言纯文本译文。
 *
 * <p>localeCode 是对 System 支持语言的逻辑引用，不建立跨 Schema 外键。记录不可变，
 * 当前服务 Store 负责数据访问和版本检查。</p>
 *
 * @param id 当前服务内部技术 ID
 * @param messageId 本服务消息 ID
 * @param localeCode BCP 47 Tag
 * @param messageText 纯文本与数字位置占位符
 * @param version 乐观锁版本
 * @param updatedAt 最近修改时间
 */
public record I18nTranslation(String id, String messageId, String localeCode, String messageText,
                              long version, Instant updatedAt) {
}
