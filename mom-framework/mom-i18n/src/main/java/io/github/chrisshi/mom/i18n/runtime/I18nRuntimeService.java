package io.github.chrisshi.mom.i18n.runtime;

import io.github.chrisshi.mom.i18n.model.I18nBundle;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.i18n.validation.I18nRules;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 当前 Owner 的匿名 Runtime Bundle 用例。
 *
 * <p>namespace 和消息 ID 分别只触发一次批量查询，不逐 Key 查询；请求语言缺失时
 * 依次回退本地 base Locale 和稳定 messageKey。数据库故障直接失败，不返回伪造的空成功。</p>
 */
public class I18nRuntimeService {
    private static final int MAX_NAMESPACES = 20;
    private final I18nStore store;
    private final I18nNamespacePolicy namespaces;
    private final I18nLocalePolicy locales;

    /** @param store 当前服务 Store @param namespaces 当前 Owner 规则 @param locales 本地回退策略 */
    public I18nRuntimeService(I18nStore store, I18nNamespacePolicy namespaces, I18nLocalePolicy locales) {
        this.store = store;
        this.namespaces = namespaces;
        this.locales = locales;
    }

    /** 返回给定 Locale 下多个 namespace 的深度只读 Bundle。 */
    @Transactional(readOnly = true)
    public I18nBundle load(String requestedLocale, List<String> requestedNamespaces) {
        if (requestedNamespaces == null || requestedNamespaces.isEmpty()
                || requestedNamespaces.size() > MAX_NAMESPACES) {
            throw new IllegalArgumentException("namespaces 数量必须在 1～20 之间");
        }
        List<String> owned = List.copyOf(new LinkedHashSet<>(requestedNamespaces.stream().map(value -> {
            String normalized = I18nRules.namespace(value);
            if (!namespaces.owns(normalized)) {
                throw new IllegalArgumentException("namespace 不属于当前服务");
            }
            return normalized;
        }).toList()));
        String effective = locales.effectiveLocale(requestedLocale);
        String base = locales.baseLocale();
        List<I18nMessage> messages = store.enabledMessages(owned);
        List<String> ids = messages.stream().map(I18nMessage::id).toList();
        List<String> localeCodes = effective.equals(base) ? List.of(base) : List.of(effective, base);
        List<I18nTranslation> translations = ids.isEmpty() ? List.of() : store.translations(ids, localeCodes);
        Map<String, Map<String, I18nTranslation>> byMessage = translations.stream()
                .collect(Collectors.groupingBy(I18nTranslation::messageId,
                        Collectors.toMap(I18nTranslation::localeCode, Function.identity())));
        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        owned.forEach(namespace -> bundles.put(namespace, new LinkedHashMap<>()));
        for (I18nMessage message : messages) {
            Map<String, I18nTranslation> localized = byMessage.getOrDefault(message.id(), Map.of());
            I18nTranslation selected = localized.getOrDefault(effective, localized.get(base));
            bundles.get(message.namespace()).put(message.messageKey(),
                    selected == null ? message.messageKey() : selected.messageText());
        }
        return new I18nBundle(requestedLocale, effective, bundles);
    }
}
