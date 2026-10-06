package io.github.chrisshi.mom.i18n.management;

import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.runtime.I18nChangePublisher;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.i18n.validation.I18nRules;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 跨 Owner 复用的 I18n 管理用例和本地事务边界。
 *
 * <p>每个宿主只注入自身 Store 与 namespace 策略；写入只触及当前 DataSource。
 * 稳定 Key 不提供 Rename，译文在同一消息行锁下校验占位符，版本冲突与唯一冲突显式失败。
 * Publisher 必须在提交后广播；事务回滚时不产生 SSE 失效信号。</p>
 */
public class I18nManagementService {
    private final I18nStore store;
    private final I18nNamespacePolicy namespaces;
    private final I18nLocalePolicy locales;
    private final I18nChangePublisher changes;

    /** @param store 当前服务唯一 Store @param namespaces 当前 Owner 规则
     * @param locales 本地 Locale 规则 @param changes 提交后失效通知 */
    public I18nManagementService(I18nStore store, I18nNamespacePolicy namespaces,
                                 I18nLocalePolicy locales, I18nChangePublisher changes) {
        this.store = store;
        this.namespaces = namespaces;
        this.locales = locales;
        this.changes = changes;
    }

    /** 创建当前 Owner 的不可改稳定键，唯一冲突返回 409。 */
    @Transactional
    public I18nMessage createMessage(String namespace, String messageKey, String description, Boolean enabled) {
        String owned = ownedNamespace(namespace);
        String key = I18nRules.messageKey(messageKey);
        try {
            I18nMessage created = store.createMessage(owned, key, I18nRules.description(description),
                    enabled == null || enabled);
            changes.bundleChanged("*", owned);
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.message_key_conflict");
        }
    }

    /** 按乐观版本修改说明和启停，不修改 namespace/messageKey。 */
    @Transactional
    public I18nMessage updateMessage(String id, String description, Boolean enabled, Long version) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        I18nMessage current = requireMessage(id, false);
        long expected = I18nRules.version(version);
        if (current.version() != expected || !store.updateMessage(current.id(),
                I18nRules.description(description), enabled, expected)) {
            throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.stale_version");
        }
        if (current.enabled() != enabled) {
            changes.bundleChanged("*", current.namespace());
        }
        return requireMessage(id, false);
    }

    /** 返回当前 Owner 单个 namespace 的管理列表。 */
    @Transactional(readOnly = true)
    public List<I18nMessage> messages(String namespace) {
        return store.messages(ownedNamespace(namespace));
    }

    /** 返回单消息全部译文，内部 ID 不作为跨服务引用。 */
    @Transactional(readOnly = true)
    public List<I18nTranslation> translations(String messageId) {
        return store.translations(requireMessage(messageId, false).id());
    }

    /**
     * 在消息行锁下创建或版本化更新译文；不同语言必须使用完全一致的位置占位符。
     *
     * <p>创建时 version 可空或 0；更新时必须与当前版本相同。写失败不重试，
     * 通知在提交后执行，不把未知结果伪装成成功。</p>
     */
    @Transactional
    public I18nTranslation saveTranslation(String messageId, String localeCode, String messageText, Long version) {
        I18nMessage message = requireMessage(messageId, true);
        String locale = locales.requireLocale(localeCode);
        Set<Integer> positions = I18nRules.placeholders(messageText);
        String text = messageText.trim();
        List<I18nTranslation> existing = store.translations(message.id());
        for (I18nTranslation translation : existing) {
            if (!translation.localeCode().equals(locale)
                    && !I18nRules.placeholders(translation.messageText()).equals(positions)) {
                throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.placeholder_mismatch");
            }
        }
        I18nTranslation current = existing.stream()
                .filter(item -> item.localeCode().equals(locale)).findFirst().orElse(null);
        I18nTranslation saved;
        if (current == null) {
            if (version != null && version != 0) {
                throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.stale_version");
            }
            try {
                saved = store.createTranslation(message.id(), locale, text);
            } catch (DataIntegrityViolationException exception) {
                throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.stale_version");
            }
        } else {
            long expected = I18nRules.version(version);
            if (current.version() != expected || !store.updateTranslation(current.id(), text, expected)) {
                throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.stale_version");
            }
            saved = store.translations(message.id()).stream()
                    .filter(item -> item.localeCode().equals(locale)).findFirst()
                    .orElseThrow(() -> new I18nFailure(I18nFailure.Kind.NOT_FOUND, "i18n.translation_not_found"));
        }
        changes.bundleChanged(locale.equals(locales.baseLocale()) ? "*" : locale, message.namespace());
        return saved;
    }

    private I18nMessage requireMessage(String id, boolean lock) {
        I18nMessage message = store.message(I18nRules.id(id), lock);
        if (message == null) {
            throw new I18nFailure(I18nFailure.Kind.NOT_FOUND, "i18n.message_not_found");
        }
        ownedNamespace(message.namespace());
        return message;
    }

    private String ownedNamespace(String value) {
        String normalized = I18nRules.namespace(value);
        if (!namespaces.owns(normalized)) {
            throw new IllegalArgumentException("namespace 不属于当前服务");
        }
        return normalized;
    }
}
