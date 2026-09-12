package io.github.chrisshi.mom.system.application.i18n;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.SystemV1Exception;
import io.github.chrisshi.mom.system.application.SystemV1Rules;
import io.github.chrisshi.mom.system.infrastructure.entity.I18nMessageEntity;
import io.github.chrisshi.mom.system.infrastructure.entity.I18nTranslationEntity;
import io.github.chrisshi.mom.system.infrastructure.mapper.I18nMessageMapper;
import io.github.chrisshi.mom.system.infrastructure.mapper.I18nTranslationMapper;
import io.github.chrisshi.mom.webmvc.i18n.I18nChangeNotifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateMessage;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.MessageView;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.SaveTranslation;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.TranslationView;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.UpdateMessage;

/**
 * System 自有 {@code system.*} 动态消息与 Translation 的用例层。
 *
 * <p>Message Key 创建后不可修改，语义变化应创建新 Key 并禁用旧 Key。Translation 更新锁定所属 Message，
 * 将同一 Message 的 placeholder 集合视为不可分割合同，避免并发写入不一致语言。所有写入位于 System
 * 单数据库本地事务；通知通过 Framework after-commit 发送，SSE 故障不影响已提交数据。该类不实现发布、
 * 回滚、快照、缓存、Outbox 或 MQ。</p>
 */
@Service
public class I18nApplication {
    private final I18nMessageMapper messageMapper;
    private final I18nTranslationMapper translationMapper;
    private final SupportedLocaleApplication localeApplication;
    private final I18nChangeNotifier notifier;

    /**
     * @param messageMapper 消息定义 Mapper
     * @param translationMapper Translation Mapper
     * @param localeApplication Locale 引用与回退用例
     * @param notifier after-commit SSE 通知器
     */
    public I18nApplication(
            I18nMessageMapper messageMapper,
            I18nTranslationMapper translationMapper,
            SupportedLocaleApplication localeApplication,
            I18nChangeNotifier notifier) {
        this.messageMapper = messageMapper;
        this.translationMapper = translationMapper;
        this.localeApplication = localeApplication;
        this.notifier = notifier;
    }

    /** 创建 namespace + messageKey 唯一且 Key 永久不可修改的消息定义。 */
    @Transactional
    public MessageView createMessage(CreateMessage command) {
        I18nMessageEntity entity = new I18nMessageEntity();
        entity.setNamespace(SystemV1Rules.namespace(command.namespace()));
        entity.setMessageKey(SystemV1Rules.messageKey(command.messageKey()));
        entity.setDescription(SystemV1Rules.description(command.description()));
        entity.setEnabled(command.enabled() == null || command.enabled());
        try {
            messageMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemV1Exception.Conflict(
                    "i18n_message_key_conflict", "system.i18n.error.message_key_conflict");
        }
        return toMessageView(entity);
    }

    /** 更新说明和启停，不接受 namespace 或 messageKey。 */
    @Transactional
    public MessageView updateMessage(String id, UpdateMessage command) {
        if (command.enabled() == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        I18nMessageEntity entity = requireMessage(id, false);
        boolean runtimeChanged = Boolean.TRUE.equals(entity.getEnabled()) != command.enabled();
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setDescription(SystemV1Rules.description(command.description()));
        entity.setEnabled(command.enabled());
        if (messageMapper.updateById(entity) != 1) {
            throw new SystemV1Exception.Conflict("stale_version", "system.i18n.error.stale_version");
        }
        if (runtimeChanged) {
            // 启停影响所有启用 Locale（包括依赖默认语言回退且尚无 Translation 的 Locale），不能只通知已有译文。
            localeApplication.supportedLocales().stream()
                    .filter(locale -> locale.enabled())
                    .map(locale -> locale.localeCode())
                    .forEach(locale -> notifier.bundleChanged(locale, entity.getNamespace()));
        }
        return toMessageView(requireMessage(id, false));
    }

    /** 返回指定 namespace 的固定 messageKey/id 排序管理列表。 */
    @Transactional(readOnly = true)
    public List<MessageView> listMessages(String namespace) {
        String ownerNamespace = SystemV1Rules.namespace(namespace);
        return messageMapper.selectList(Wrappers.<I18nMessageEntity>lambdaQuery()
                        .eq(I18nMessageEntity::getNamespace, ownerNamespace)
                        .orderByAsc(I18nMessageEntity::getMessageKey)
                        .orderByAsc(I18nMessageEntity::getId))
                .stream().map(I18nApplication::toMessageView).toList();
    }

    /**
     * 新增或版本化更新单条 Translation。
     *
     * <p>创建时 version 可为空或 0；更新时必须等于当前版本。相同 Message 的所有 Locale 必须具有完全
     * 一致的数字 placeholder 集合。</p>
     */
    @Transactional
    public TranslationView saveTranslation(String messageId, String localeCode, SaveTranslation command) {
        I18nMessageEntity message = requireMessage(messageId, true);
        String locale = localeApplication.requireLocale(localeCode).getLocaleCode();
        Set<Integer> placeholders = SystemV1Rules.placeholders(command.messageText());
        List<I18nTranslationEntity> translations = translationMapper.selectList(
                Wrappers.<I18nTranslationEntity>lambdaQuery()
                        .eq(I18nTranslationEntity::getMessageId, message.getId()));
        translations.stream()
                .filter(existing -> !existing.getLocaleCode().equals(locale))
                .map(existing -> SystemV1Rules.placeholders(existing.getMessageText()))
                .filter(existing -> !existing.equals(placeholders))
                .findAny()
                .ifPresent(ignored -> {
                    throw new SystemV1Exception.Conflict(
                            "placeholder_mismatch", "system.i18n.error.placeholder_mismatch");
                });

        I18nTranslationEntity current = translations.stream()
                .filter(existing -> existing.getLocaleCode().equals(locale))
                .findFirst().orElse(null);
        I18nTranslationEntity saved;
        if (current == null) {
            if (command.version() != null && command.version() != 0) {
                throw new SystemV1Exception.Conflict("stale_version", "system.i18n.error.stale_version");
            }
            saved = new I18nTranslationEntity();
            saved.setMessageId(message.getId());
            saved.setLocaleCode(locale);
            saved.setMessageText(command.messageText().trim());
            try {
                translationMapper.insert(saved);
            } catch (DataIntegrityViolationException exception) {
                throw new SystemV1Exception.Conflict("stale_version", "system.i18n.error.stale_version");
            }
        } else {
            current.setVersion(SystemV1Rules.version(command.version()));
            current.setMessageText(command.messageText().trim());
            if (translationMapper.updateById(current) != 1) {
                throw new SystemV1Exception.Conflict("stale_version", "system.i18n.error.stale_version");
            }
            saved = translationMapper.selectById(current.getId());
        }
        notifier.bundleChanged(locale, message.getNamespace());
        return toTranslationView(saved);
    }

    /** 返回单个 Message 的全部 Translation，按 localeCode/id 固定排序。 */
    @Transactional(readOnly = true)
    public List<TranslationView> listTranslations(String messageId) {
        I18nMessageEntity message = requireMessage(messageId, false);
        return translationMapper.selectList(Wrappers.<I18nTranslationEntity>lambdaQuery()
                        .eq(I18nTranslationEntity::getMessageId, message.getId())
                        .orderByAsc(I18nTranslationEntity::getLocaleCode)
                        .orderByAsc(I18nTranslationEntity::getId))
                .stream().map(I18nApplication::toTranslationView).toList();
    }

    private I18nMessageEntity requireMessage(String id, boolean forUpdate) {
        var query = Wrappers.<I18nMessageEntity>lambdaQuery()
                .eq(I18nMessageEntity::getId, SystemV1Rules.id(id));
        if (forUpdate) {
            query.last("FOR UPDATE");
        }
        I18nMessageEntity entity = messageMapper.selectOne(query);
        if (entity == null) {
            throw new SystemV1Exception.NotFound("system.i18n.error.message_not_found");
        }
        return entity;
    }

    private static MessageView toMessageView(I18nMessageEntity entity) {
        return new MessageView(entity.getId(), entity.getNamespace(), entity.getMessageKey(),
                entity.getDescription(), Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(),
                entity.getUpdatedAt());
    }

    private static TranslationView toTranslationView(I18nTranslationEntity entity) {
        return new TranslationView(entity.getId(), entity.getMessageId(), entity.getLocaleCode(),
                entity.getMessageText(), entity.getVersion(), entity.getUpdatedAt());
    }
}
