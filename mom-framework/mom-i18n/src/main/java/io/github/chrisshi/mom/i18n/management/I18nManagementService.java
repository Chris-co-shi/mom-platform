package io.github.chrisshi.mom.i18n.management;

import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.runtime.I18nChangePublisher;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.i18n.validation.I18nRules;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 跨 Owner 复用的 I18n 管理用例和本地事务边界。
 *
 * <p>Owner 由当前服务注入的 Store 决定，稳定 Key 在该 Owner 内唯一且不可改名。译文在本地事务中
 * 按 Locale 版本化；重复译文查询只是提交前提醒，不参与保存判定。通知在事务提交后执行。</p>
 */
public class I18nManagementService {
    private final I18nStore store;
    private final I18nLocalePolicy locales;
    private final I18nChangePublisher changes;

    /** @param store 当前服务唯一 Store @param locales 本地 Locale 规则 @param changes 提交后失效通知 */
    public I18nManagementService(I18nStore store, I18nLocalePolicy locales, I18nChangePublisher changes) {
        this.store = store;
        this.locales = locales;
        this.changes = changes;
    }

    /** 创建当前 Owner 的不可改稳定键，重复 Key 返回冲突。 */
    @Transactional
    public I18nMessage createMessage(String messageKey, String description, Boolean enabled) {
        String key = I18nRules.messageKey(messageKey);
        try {
            I18nMessage created = store.createMessage(key, I18nRules.description(description), enabled == null || enabled);
            changes.bundleChanged("*");
            return created;
        } catch (DuplicateKeyException exception) {
            throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.message_key_conflict");
        }
    }

    /** 按乐观版本修改说明和启停，不修改稳定 Key。 */
    @Transactional
    public I18nMessage updateMessage(String id, String description, Boolean enabled, Long version) {
        if (enabled == null) throw new IllegalArgumentException("enabled 不能为空");
        I18nMessage current = requireMessage(id, false);
        long expected = I18nRules.version(version);
        if (current.version() != expected || !store.updateMessage(current.id(),
                I18nRules.description(description), enabled, expected)) {
            throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.stale_version");
        }
        if (current.enabled() != enabled) changes.bundleChanged("*");
        return requireMessage(id, false);
    }

    /** 返回当前 Owner 的全部定义，包含停用项；不跨服务查询。 */
    @Transactional(readOnly = true)
    public List<I18nMessage> messages() {
        return store.messages();
    }

    /** 返回单消息全部译文，内部 ID 不作为跨服务引用。 */
    @Transactional(readOnly = true)
    public List<I18nTranslation> translations(String messageId) {
        return store.translations(requireMessage(messageId, false).id());
    }

    /**
     * 查询当前 Owner 中精确相同的译文 Key，仅用于前端提醒；不会锁定文本或阻止保存。
     * @param messageId 正在编辑的消息 ID
     * @param localeCode BCP 47 Locale
     * @param messageText 待保存原文
     * @return 其他使用相同译文的稳定 Key
     */
    @Transactional(readOnly = true)
    public List<String> duplicateMessageKeys(String messageId, String localeCode, String messageText) {
        I18nMessage message = requireMessage(messageId, false);
        String locale = locales.requireLocale(localeCode);
        String text = I18nRules.translationText(messageText);
        return store.duplicateMessageKeys(message.id(), locale, text);
    }

    /**
     * 在消息行锁下创建或版本化更新译文；不同语言必须使用完全一致的位置占位符。
     * 创建时 version 可空或 0；更新时必须与当前版本相同。数据库写失败不重试。
     */
    @Transactional
    public I18nTranslation saveTranslation(String messageId, String localeCode, String messageText, Long version) {
        I18nMessage message = requireMessage(messageId, true);
        String locale = locales.requireLocale(localeCode);
        Set<Integer> positions = I18nRules.placeholders(messageText);
        String text = I18nRules.translationText(messageText);
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
            if (version != null && version != 0) throw new I18nFailure(I18nFailure.Kind.CONFLICT, "i18n.stale_version");
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
            saved = store.translations(message.id()).stream().filter(item -> item.localeCode().equals(locale)).findFirst()
                    .orElseThrow(() -> new I18nFailure(I18nFailure.Kind.NOT_FOUND, "i18n.translation_not_found"));
        }
        changes.bundleChanged(locale.equals(locales.baseLocale()) ? "*" : locale);
        return saved;
    }

    private I18nMessage requireMessage(String id, boolean lock) {
        I18nMessage message = store.message(I18nRules.id(id), lock);
        if (message == null) throw new I18nFailure(I18nFailure.Kind.NOT_FOUND, "i18n.message_not_found");
        return message;
    }
}
