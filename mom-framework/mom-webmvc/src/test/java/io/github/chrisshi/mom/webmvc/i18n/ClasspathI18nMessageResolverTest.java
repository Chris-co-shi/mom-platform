package io.github.chrisshi.mom.webmvc.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Framework classpath 消息所有权和确定性回退的快速单元测试。
 */
class ClasspathI18nMessageResolverTest {
    private final ClasspathI18nMessageResolver resolver = new ClasspathI18nMessageResolver();

    /** 中文、英文、缺失 Locale 与缺失 Key 必须按固定顺序解析。 */
    @Test
    void shouldResolveBuiltInLocalesAndFallbackWithoutSystemDependency() {
        assertThat(resolver.resolve("framework.security.unauthorized", Locale.forLanguageTag("zh-CN")))
                .isEqualTo("未认证或访问令牌无效");
        assertThat(resolver.resolve("framework.security.unauthorized", Locale.forLanguageTag("en-US")))
                .isEqualTo("Authentication is required or the access token is invalid");
        assertThat(resolver.resolve("framework.security.unauthorized", Locale.forLanguageTag("cs-CZ")))
                .isEqualTo("未认证或访问令牌无效");
        assertThat(resolver.resolve("framework.missing", Locale.forLanguageTag("en-US")))
                .isEqualTo("framework.missing");
    }
}
