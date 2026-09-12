package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * 从 Framework 自己的 classpath ResourceBundle 解析技术消息。
 *
 * <p>实例在构造后不可变且可并发复用。解析顺序固定为请求 Locale、Framework 内置默认
 * {@code zh-CN}、消息键；它关闭宿主机默认 Locale 回退，因此部署机器语言不会改变 API 行为。
 * 该实现不访问数据库、Redis、System 服务或网络，外部基础设施不可用时仍可完成技术异常翻译。</p>
 */
public final class ClasspathI18nMessageResolver implements I18nMessageResolver {
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("zh-CN");

    private final ResourceBundleMessageSource messageSource;

    /** 创建只读取 Framework 内置资源的解析器。 */
    public ClasspathI18nMessageResolver() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/framework-messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        this.messageSource = source;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本方法无副作用且幂等；相同资源版本和输入产生相同文本。</p>
     */
    @Override
    public String resolve(String messageKey, Locale locale, Object... args) {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("messageKey 不能为空");
        }
        Locale requested = locale == null ? DEFAULT_LOCALE : locale;
        String resolved = messageSource.getMessage(messageKey, args, null, requested);
        if (resolved != null) {
            return resolved;
        }
        return messageSource.getMessage(messageKey, args, messageKey, DEFAULT_LOCALE);
    }
}
