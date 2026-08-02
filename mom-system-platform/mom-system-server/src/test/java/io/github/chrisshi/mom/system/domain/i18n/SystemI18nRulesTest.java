package io.github.chrisshi.mom.system.domain.i18n;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dynamic I18n Locale、Code、文本、Placeholder、fallback、排序与 checksum 纯领域测试。 */
class SystemI18nRulesTest {
    @Test
    void localeAndCodesMustBeStrictAndCanonical() {
        assertThat(SystemI18nRules.normalizeApplicationCode(" MOM-Web ")).isEqualTo("mom-web");
        assertThat(SystemI18nRules.normalizeResourceCode(" Common ")).isEqualTo("common");
        assertThat(SystemI18nRules.requireLocale("zh-CN")).isEqualTo("zh-CN");
        assertThatThrownBy(() -> SystemI18nRules.requireLocale("zh-cn"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.normalizeApplicationCode("iam/client"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.requireMessageKey("1bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messageValueMustRejectControlsAndExpressionSyntax() {
        assertThat(SystemI18nRules.validateMessageValue("Hello {username}, {count}"))
                .containsExactly("username", "count");
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("bad\u0000value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("${username}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("#{T(java.lang.Runtime)}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("<script>alert(1)</script>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("{count, plural, one{x}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeholderParserMustRejectEmptyInvalidAndUnbalancedBraces() {
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("{1name}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("{name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemI18nRules.validateMessageValue("name}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishMustFallbackMissingAlternativeAndKeepSortedDeterministicJson() {
        var snapshots = SystemI18nRules.buildSnapshots("zh-CN", List.of(
                draft("z.last", "zh-CN", "最后 {count}"),
                draft("a.first", "zh-CN", "你好 {username}"),
                draft("a.first", "en-US", "Hello {username}")));

        assertThat(snapshots.get("zh-CN").messages().keySet()).containsExactly("a.first", "z.last");
        assertThat(snapshots.get("en-US").messages().get("z.last")).isEqualTo("最后 {count}");
        assertThat(snapshots.get("en-US").fallbackCount()).isEqualTo(1);
        assertThat(snapshots.get("zh-CN").fallbackCount()).isZero();
        assertThat(snapshots.get("zh-CN").json()).startsWith("{\"a.first\":");
        assertThat(snapshots.get("zh-CN").checksum()).matches("[0-9a-f]{64}");
        assertThat(SystemI18nRules.snapshot(snapshots.get("zh-CN").messages(), 0).checksum())
                .isEqualTo(snapshots.get("zh-CN").checksum());
    }

    @Test
    void publishMustRequireDefaultLocaleAndMatchingPlaceholderSets() {
        assertThatThrownBy(() -> SystemI18nRules.buildSnapshots("zh-CN", List.of(
                draft("hello", "en-US", "Hello"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultLocale");
        assertThatThrownBy(() -> SystemI18nRules.buildSnapshots("zh-CN", List.of(
                draft("hello", "zh-CN", "你好 {username}"),
                draft("hello", "en-US", "Hello {count}"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Placeholder Set");
    }

    @Test
    void jsonChecksumInputMustEscapePlainTextWithoutExecutingIt() {
        var snapshot = SystemI18nRules.snapshot(Map.of("plain", "<b>\"x\"</b>\nline"), 0);
        assertThat(snapshot.json()).isEqualTo("{\"plain\":\"<b>\\\"x\\\"</b>\\nline\"}");
        assertThat(snapshot.messages().get("plain")).isEqualTo("<b>\"x\"</b>\nline");
    }

    private static SystemI18nRules.DraftValue draft(String key, String locale, String value) {
        return new SystemI18nRules.DraftValue(key, locale, value);
    }
}
