package io.github.chrisshi.mom.i18n.runtime;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.i18n.model.I18nBundle;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 当前服务 Owner 的匿名 Runtime Bundle 用例。
 *
 * <p>每次请求批量读取当前 Owner 全部启用消息及所需 Locale 译文，不逐 Key 查询。
 * 请求语言缺失时回退本地 base Locale，再回退稳定 messageKey；数据库故障直接失败。</p>
 */
@RequiredArgsConstructor
public class I18nRuntimeService {
    private final I18nStore store;
    private final I18nLocalePolicy locales;

    

    /** 返回当前 Owner 在给定 Locale 下的扁平只读译文表。 */
    @Transactional(readOnly = true)
    public I18nBundle load(String requestedLocale) {
        String effective = locales.effectiveLocale(requestedLocale);
        String base = locales.baseLocale();
        List<I18nMessage> messages = store.enabledMessages();
        List<String> ids = messages.stream().map(I18nMessage::id).toList();
        List<String> localeCodes = effective.equals(base) ? List.of(base) : List.of(effective, base);
        List<I18nTranslation> translations = ids.isEmpty() ? List.of() : store.translations(ids, localeCodes);
        Map<String, Map<String, I18nTranslation>> byMessage = translations.stream()
                .collect(Collectors.groupingBy(I18nTranslation::messageId,
                        Collectors.toMap(I18nTranslation::localeCode, Function.identity())));
        Map<String, String> bundle = new LinkedHashMap<>();
        for (I18nMessage message : messages) {
            Map<String, I18nTranslation> localized = byMessage.getOrDefault(message.id(), Map.of());
            I18nTranslation selected = localized.getOrDefault(effective, localized.get(base));
            bundle.put(message.messageKey(), selected == null ? message.messageKey() : selected.messageText());
        }
        return new I18nBundle(requestedLocale, effective, bundle);
    }
}
