package io.github.chrisshi.mom.mdm.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.mdm.infrastructure.entity.I18nMessageEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.I18nTranslationEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.I18nMessageMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.I18nTranslationMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MDM 自有两张 I18n 表到 Framework Store SPI 的数据库适配器。
 *
 * <p>只访问 mom_mdm Schema 和现有 Mapper/Entity，不复制公共管理/回退规则。
 * 调用方负责本地事务；更新使用 MyBatis-Plus Version CAS，失败返回 false。
 * 数据库不可用时异常向上传播，不返回空成功。</p>
 */
@Component
public class MdmI18nStore implements I18nStore {
    private final I18nMessageMapper messages;
    private final I18nTranslationMapper translations;

    /** @param messages MDM Message Mapper @param translations MDM Translation Mapper */
    public MdmI18nStore(I18nMessageMapper messages, I18nTranslationMapper translations) {
        this.messages = messages;
        this.translations = translations;
    }

    /** {@inheritDoc} */
    @Override
    public I18nMessage message(String id, boolean forUpdate) {
        var query = Wrappers.<I18nMessageEntity>lambdaQuery().eq(I18nMessageEntity::getId, id);
        if (forUpdate) {
            query.last("FOR UPDATE");
        }
        I18nMessageEntity entity = messages.selectOne(query);
        return entity == null ? null : toMessage(entity);
    }

    /** {@inheritDoc} */
    @Override
    public I18nMessage message(String namespace, String messageKey) {
        I18nMessageEntity entity = messages.selectOne(Wrappers.<I18nMessageEntity>lambdaQuery()
                .eq(I18nMessageEntity::getNamespace, namespace)
                .eq(I18nMessageEntity::getMessageKey, messageKey));
        return entity == null ? null : toMessage(entity);
    }

    /** {@inheritDoc} */
    @Override
    public List<I18nMessage> messages(String namespace) {
        return messages.selectList(Wrappers.<I18nMessageEntity>lambdaQuery()
                        .eq(I18nMessageEntity::getNamespace, namespace)
                        .orderByAsc(I18nMessageEntity::getMessageKey)
                        .orderByAsc(I18nMessageEntity::getId))
                .stream().map(MdmI18nStore::toMessage).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<I18nMessage> enabledMessages(List<String> namespaces) {
        if (namespaces.isEmpty()) return List.of();
        return messages.selectList(Wrappers.<I18nMessageEntity>lambdaQuery()
                        .in(I18nMessageEntity::getNamespace, namespaces)
                        .eq(I18nMessageEntity::getEnabled, true)
                        .orderByAsc(I18nMessageEntity::getNamespace)
                        .orderByAsc(I18nMessageEntity::getMessageKey)
                        .orderByAsc(I18nMessageEntity::getId))
                .stream().map(MdmI18nStore::toMessage).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<I18nTranslation> translations(String messageId) {
        return translations.selectList(Wrappers.<I18nTranslationEntity>lambdaQuery()
                        .eq(I18nTranslationEntity::getMessageId, messageId)
                        .orderByAsc(I18nTranslationEntity::getLocaleCode)
                        .orderByAsc(I18nTranslationEntity::getId))
                .stream().map(MdmI18nStore::toTranslation).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<I18nTranslation> translations(List<String> messageIds, List<String> localeCodes) {
        if (messageIds.isEmpty() || localeCodes.isEmpty()) return List.of();
        return translations.selectList(Wrappers.<I18nTranslationEntity>lambdaQuery()
                        .in(I18nTranslationEntity::getMessageId, messageIds)
                        .in(I18nTranslationEntity::getLocaleCode, localeCodes))
                .stream().map(MdmI18nStore::toTranslation).toList();
    }

    /** {@inheritDoc} */
    @Override
    public I18nMessage createMessage(String namespace, String messageKey, String description, boolean enabled) {
        I18nMessageEntity entity = new I18nMessageEntity();
        entity.setNamespace(namespace);
        entity.setMessageKey(messageKey);
        entity.setDescription(description);
        entity.setEnabled(enabled);
        messages.insert(entity);
        return toMessage(entity);
    }

    /** {@inheritDoc} */
    @Override
    public boolean updateMessage(String id, String description, boolean enabled, long version) {
        I18nMessageEntity entity = messages.selectById(id);
        if (entity == null) return false;
        entity.setVersion(version);
        entity.setDescription(description);
        entity.setEnabled(enabled);
        return messages.updateById(entity) == 1;
    }

    /** {@inheritDoc} */
    @Override
    public I18nTranslation createTranslation(String messageId, String localeCode, String messageText) {
        I18nTranslationEntity entity = new I18nTranslationEntity();
        entity.setMessageId(messageId);
        entity.setLocaleCode(localeCode);
        entity.setMessageText(messageText);
        translations.insert(entity);
        return toTranslation(entity);
    }

    /** {@inheritDoc} */
    @Override
    public boolean updateTranslation(String id, String messageText, long version) {
        I18nTranslationEntity entity = translations.selectById(id);
        if (entity == null) return false;
        entity.setVersion(version);
        entity.setMessageText(messageText);
        return translations.updateById(entity) == 1;
    }

    private static I18nMessage toMessage(I18nMessageEntity entity) {
        return new I18nMessage(entity.getId(), entity.getNamespace(), entity.getMessageKey(),
                entity.getDescription(), Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(),
                entity.getUpdatedAt());
    }

    private static I18nTranslation toTranslation(I18nTranslationEntity entity) {
        return new I18nTranslation(entity.getId(), entity.getMessageId(), entity.getLocaleCode(),
                entity.getMessageText(), entity.getVersion(), entity.getUpdatedAt());
    }
}
