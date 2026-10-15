package io.github.chrisshi.mom.qms.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.qms.infrastructure.entity.QmsI18nMessageEntity;
import io.github.chrisshi.mom.qms.infrastructure.entity.QmsI18nTranslationEntity;
import io.github.chrisshi.mom.qms.infrastructure.mapper.QmsI18nMessageMapper;
import io.github.chrisshi.mom.qms.infrastructure.mapper.QmsI18nTranslationMapper;
import org.springframework.stereotype.Component;
import java.util.List;

/** QMS 自有 I18n 表对 Framework Store SPI 的适配器。 */
@Component
public class QmsI18nStore implements I18nStore {
    private final QmsI18nMessageMapper messages;
    private final QmsI18nTranslationMapper translations;
    public QmsI18nStore(QmsI18nMessageMapper messages, QmsI18nTranslationMapper translations) {
        this.messages = messages; this.translations = translations;
    }
    @Override public I18nMessage message(String id, boolean forUpdate) {
        var query = Wrappers.<QmsI18nMessageEntity>lambdaQuery().eq(QmsI18nMessageEntity::getId, id);
        if (forUpdate) query.last("FOR UPDATE");
        return toMessage(messages.selectOne(query));
    }
    @Override public I18nMessage message(String key) {
        return toMessage(messages.selectOne(Wrappers.<QmsI18nMessageEntity>lambdaQuery()
                .eq(QmsI18nMessageEntity::getMessageKey, key)));
    }
    @Override public List<I18nMessage> messages() {
        return messages.selectList(Wrappers.<QmsI18nMessageEntity>lambdaQuery()
                .orderByAsc(QmsI18nMessageEntity::getMessageKey)).stream().map(QmsI18nStore::toMessage).toList();
    }
    @Override public List<I18nMessage> enabledMessages() {
        return messages.selectList(Wrappers.<QmsI18nMessageEntity>lambdaQuery().eq(QmsI18nMessageEntity::getEnabled, true)
                .orderByAsc(QmsI18nMessageEntity::getMessageKey)).stream().map(QmsI18nStore::toMessage).toList();
    }
    @Override public List<I18nTranslation> translations(String messageId) {
        return translations.selectList(Wrappers.<QmsI18nTranslationEntity>lambdaQuery()
                .eq(QmsI18nTranslationEntity::getMessageId, messageId)
                .orderByAsc(QmsI18nTranslationEntity::getLocaleCode)).stream().map(QmsI18nStore::toTranslation).toList();
    }
    @Override public List<I18nTranslation> translations(List<String> messageIds, List<String> locales) {
        if (messageIds.isEmpty() || locales.isEmpty()) return List.of();
        return translations.selectList(Wrappers.<QmsI18nTranslationEntity>lambdaQuery()
                .in(QmsI18nTranslationEntity::getMessageId, messageIds)
                .in(QmsI18nTranslationEntity::getLocaleCode, locales)).stream().map(QmsI18nStore::toTranslation).toList();
    }
    @Override public I18nMessage createMessage(String key, String description, boolean enabled) {
        QmsI18nMessageEntity entity = new QmsI18nMessageEntity(); entity.setMessageKey(key);
        entity.setDescription(description); entity.setEnabled(enabled); messages.insert(entity); return toMessage(entity);
    }
    @Override public List<String> duplicateMessageKeys(String currentId, String locale, String text) {
        List<String> ids = translations.selectList(Wrappers.<QmsI18nTranslationEntity>lambdaQuery()
                .eq(QmsI18nTranslationEntity::getLocaleCode, locale).eq(QmsI18nTranslationEntity::getMessageText, text)
                .ne(QmsI18nTranslationEntity::getMessageId, currentId)).stream()
                .map(QmsI18nTranslationEntity::getMessageId).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return messages.selectList(Wrappers.<QmsI18nMessageEntity>lambdaQuery().in(QmsI18nMessageEntity::getId, ids)
                .orderByAsc(QmsI18nMessageEntity::getMessageKey)).stream().map(QmsI18nMessageEntity::getMessageKey).toList();
    }
    @Override public boolean updateMessage(String id, String description, boolean enabled, long version) {
        QmsI18nMessageEntity entity = messages.selectById(id); if (entity == null) return false;
        entity.setVersion(version); entity.setDescription(description); entity.setEnabled(enabled);
        return messages.updateById(entity) == 1;
    }
    @Override public I18nTranslation createTranslation(String messageId, String locale, String text) {
        QmsI18nTranslationEntity entity = new QmsI18nTranslationEntity(); entity.setMessageId(messageId);
        entity.setLocaleCode(locale); entity.setMessageText(text); translations.insert(entity); return toTranslation(entity);
    }
    @Override public boolean updateTranslation(String id, String text, long version) {
        QmsI18nTranslationEntity entity = translations.selectById(id); if (entity == null) return false;
        entity.setVersion(version); entity.setMessageText(text); return translations.updateById(entity) == 1;
    }
    private static I18nMessage toMessage(QmsI18nMessageEntity entity) {
        return entity == null ? null : new I18nMessage(entity.getId(), entity.getMessageKey(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getUpdatedAt());
    }
    private static I18nTranslation toTranslation(QmsI18nTranslationEntity entity) {
        return new I18nTranslation(entity.getId(), entity.getMessageId(), entity.getLocaleCode(), entity.getMessageText(),
                entity.getVersion(), entity.getUpdatedAt());
    }
}
