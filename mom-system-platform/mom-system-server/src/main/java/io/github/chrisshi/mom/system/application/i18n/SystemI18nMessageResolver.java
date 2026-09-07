package io.github.chrisshi.mom.system.application.i18n;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.i18n.ClasspathI18nMessageResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 在 System 服务内组合动态 {@code system.*} 与 classpath {@code framework.*} 消息的解析器。
 *
 * <p>Framework 消息始终本地解析；System 消息从本服务 Provider 读取并遵守 SupportedLocale fallback。
 * 该类不请求远程服务，也不改变消息数据 ownership。数据库读取失败会向上暴露，不把基础设施故障伪装成
 * 业务成功；不存在的 Key 最终返回原始 Key。</p>
 */
@Primary
@Component
public class SystemI18nMessageResolver implements I18nMessageResolver {
    private final SystemI18nRuntimeProvider provider;
    private final ClasspathI18nMessageResolver frameworkResolver = new ClasspathI18nMessageResolver();

    /** @param provider System 本地动态 Bundle Provider */
    public SystemI18nMessageResolver(SystemI18nRuntimeProvider provider) {
        this.provider = provider;
    }

    /** {@inheritDoc} */
    @Override
    public String resolve(String messageKey, Locale locale, Object... args) {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("messageKey 不能为空");
        }
        if (!messageKey.startsWith("system.")) {
            return frameworkResolver.resolve(messageKey, locale, args);
        }
        String relativeKey = messageKey.substring("system.".length());
        String localeCode = locale == null ? "zh-CN" : locale.toLanguageTag();
        Map<String, String> messages = provider.load(localeCode, List.of("system"))
                .bundles().getOrDefault("system", Map.of());
        String pattern = messages.getOrDefault(relativeKey, messageKey);
        return new MessageFormat(pattern, locale == null ? Locale.forLanguageTag("zh-CN") : locale).format(args);
    }
}
