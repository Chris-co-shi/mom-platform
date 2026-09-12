package io.github.chrisshi.mom.system.application.i18n;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.SystemV1Rules;
import io.github.chrisshi.mom.system.infrastructure.entity.I18nMessageEntity;
import io.github.chrisshi.mom.system.infrastructure.entity.I18nTranslationEntity;
import io.github.chrisshi.mom.system.infrastructure.mapper.I18nMessageMapper;
import io.github.chrisshi.mom.system.infrastructure.mapper.I18nTranslationMapper;
import io.github.chrisshi.mom.webmvc.i18n.I18nRuntimeProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 从 System 本地 PostgreSQL 组装 {@code system.*} Runtime Bundle 的 Provider。
 *
 * <p>该实现只读取启用 Message 和当前 Translation，不使用旧 Release/Snapshot/Cache 表。请求 Locale 不存在
 * 或禁用时整体回退默认 Locale；单 Key 缺失时依次回退默认 Locale、messageKey。读取是无副作用的本地
 *事务，数据库故障显式失败，不调用远程 System 或其他业务服务。</p>
 */
@Component
public class SystemI18nRuntimeProvider implements I18nRuntimeProvider {
    private final SupportedLocaleApplication localeApplication;
    private final I18nMessageMapper messageMapper;
    private final I18nTranslationMapper translationMapper;

    /**
     * @param localeApplication Locale fallback 规则
     * @param messageMapper 消息定义 Mapper
     * @param translationMapper Translation Mapper
     */
    public SystemI18nRuntimeProvider(
            SupportedLocaleApplication localeApplication,
            I18nMessageMapper messageMapper,
            I18nTranslationMapper translationMapper) {
        this.localeApplication = localeApplication;
        this.messageMapper = messageMapper;
        this.translationMapper = translationMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public I18nRuntimeBundle load(String localeCode, List<String> namespaces) {
        if (namespaces == null || namespaces.isEmpty() || namespaces.size() > 20) {
            throw new IllegalArgumentException("namespaces 数量必须在 1～20 之间");
        }
        List<String> normalizedNamespaces = namespaces.stream()
                .map(SystemV1Rules::namespace).distinct().toList();
        I18nModels.LocaleResolution locale = localeApplication.resolve(localeCode);
        List<I18nMessageEntity> messages = messageMapper.selectList(
                Wrappers.<I18nMessageEntity>lambdaQuery()
                        .in(I18nMessageEntity::getNamespace, normalizedNamespaces)
                        .eq(I18nMessageEntity::getEnabled, true)
                        .orderByAsc(I18nMessageEntity::getNamespace)
                        .orderByAsc(I18nMessageEntity::getMessageKey)
                        .orderByAsc(I18nMessageEntity::getId));
        List<String> messageIds = messages.stream().map(I18nMessageEntity::getId).toList();
        List<I18nTranslationEntity> translations = messageIds.isEmpty() ? List.of()
                : translationMapper.selectList(Wrappers.<I18nTranslationEntity>lambdaQuery()
                .in(I18nTranslationEntity::getMessageId, messageIds)
                .in(I18nTranslationEntity::getLocaleCode,
                        locale.effectiveLocale().equals(locale.defaultLocale())
                                ? List.of(locale.defaultLocale())
                                : List.of(locale.effectiveLocale(), locale.defaultLocale())));
        Map<String, Map<String, I18nTranslationEntity>> byMessage = translations.stream()
                .collect(Collectors.groupingBy(I18nTranslationEntity::getMessageId,
                        Collectors.toMap(I18nTranslationEntity::getLocaleCode, Function.identity())));
        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        normalizedNamespaces.forEach(namespace -> bundles.put(namespace, new LinkedHashMap<>()));
        for (I18nMessageEntity message : messages) {
            Map<String, I18nTranslationEntity> localized = byMessage.getOrDefault(message.getId(), Map.of());
            I18nTranslationEntity selected = localized.get(locale.effectiveLocale());
            if (selected == null) {
                selected = localized.get(locale.defaultLocale());
            }
            String text = selected == null ? message.getMessageKey() : selected.getMessageText();
            bundles.get(message.getNamespace()).put(message.getMessageKey(), text);
        }
        return new I18nRuntimeBundle(locale.requestedLocale(), locale.effectiveLocale(), bundles);
    }
}
