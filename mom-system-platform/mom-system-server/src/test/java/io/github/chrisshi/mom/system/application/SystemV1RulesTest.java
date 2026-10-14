package io.github.chrisshi.mom.system.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System V1 Locale、普通文本与 placeholder 合同的快速规则测试。
 */
class SystemV1RulesTest {

    /** Locale 必须规范为 BCP 47。 */
    @Test
    void shouldNormalizeLocale() {
        assertThat(SystemV1Rules.localeCode("zh-cn")).isEqualTo("zh-CN");
        assertThat(SystemV1Rules.localeCode("cs-CZ")).isEqualTo("cs-CZ");
    }

    /** V1 只允许普通文本和数字位置 placeholder。 */
    @Test
    void shouldRejectHtmlAndUnsupportedPlaceholderSyntax() {
        assertThat(SystemV1Rules.placeholders("物料 {0} 在工厂 {1} 不存在"))
                .containsExactlyInAnyOrder(0, 1);
        assertThatThrownBy(() -> SystemV1Rules.placeholders("<b>危险</b>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemV1Rules.placeholders("Hello {name}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemV1Rules.placeholders("Hello {0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
