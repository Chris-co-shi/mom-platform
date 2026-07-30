package io.github.chrisshi.mom.system.domain.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** System Parameter 纯领域规则与五种值类型规范化测试。 */
class SystemParameterRulesTest {
    private final ParameterValueNormalizer normalizer = new ParameterValueNormalizer(JsonMapper.builder().build());

    @Test
    void globalScopeMustUseCanonicalEmptyCode() {
        assertThat(SystemParameterRules.normalizeScopeCode(ParameterScopeType.GLOBAL, null)).isEmpty();
        assertThat(SystemParameterRules.normalizeScopeCode(ParameterScopeType.GLOBAL, "  ")).isEmpty();
        assertThatThrownBy(() -> SystemParameterRules.normalizeScopeCode(ParameterScopeType.GLOBAL, "mom-web"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationScopeMustNormalizeLowercaseKebabCase() {
        assertThat(SystemParameterRules.normalizeScopeCode(ParameterScopeType.APPLICATION, "MOM-WEB"))
                .isEqualTo("mom-web");
        assertThatThrownBy(() -> SystemParameterRules.normalizeScopeCode(ParameterScopeType.APPLICATION, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemParameterRules.normalizeScopeCode(ParameterScopeType.APPLICATION, "mom_web"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyMustNormalizeAndRejectInvalidFormat() {
        assertThat(SystemParameterRules.normalizeKey(" Feature.Order_Timeout "))
                .isEqualTo("feature.order_timeout");
        assertThatThrownBy(() -> SystemParameterRules.normalizeKey("1feature.timeout"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemParameterRules.normalizeKey("feature..timeout"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "db.password", "db.passwd", "db.pwd", "auth.secret", "auth.token", "auth.credential",
            "tls.private-key", "tls.private_key", "tls.privatekey", "oauth.client-secret",
            "oauth.client_secret", "oauth.clientsecret", "cloud.access-key", "cloud.access_key",
            "cloud.accesskey", "service.api-key", "service.api_key", "service.apikey"
    })
    void secretLikeKeyMustBeRejected(String key) {
        assertThatThrownBy(() -> SystemParameterRules.normalizeKey(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("System Parameter 不允许保存 Secret 或 Credential");
    }

    @Test
    void stringMustPreserveMeaningfulSpacesAndRejectControls() {
        assertThat(normalizer.normalize(ParameterValueType.STRING, "  MOM value  "))
                .isEqualTo("  MOM value  ");
        assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.STRING, "line\nvalue"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.STRING, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void integerMustUseCanonicalDecimalString() {
        assertThat(normalizer.normalize(ParameterValueType.INTEGER, "00012")).isEqualTo("12");
        assertThat(normalizer.normalize(ParameterValueType.INTEGER, "+12")).isEqualTo("12");
        assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.INTEGER, "12.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimalMustUsePlainStringWithoutMeaninglessZeros() {
        assertThat(normalizer.normalize(ParameterValueType.DECIMAL, "12.5000")).isEqualTo("12.5");
        assertThat(normalizer.normalize(ParameterValueType.DECIMAL, "1E+3")).isEqualTo("1000");
        assertThat(normalizer.normalize(ParameterValueType.DECIMAL, "-0.000")).isEqualTo("0");
        assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.DECIMAL, "number"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void booleanMustOnlyAcceptExplicitCanonicalValues() {
        assertThat(normalizer.normalize(ParameterValueType.BOOLEAN, "true")).isEqualTo("true");
        assertThat(normalizer.normalize(ParameterValueType.BOOLEAN, "false")).isEqualTo("false");
        for (String invalid : new String[]{"TRUE", "1", "0", "yes", "no", "on", "off"}) {
            assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.BOOLEAN, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void jsonMustUseSharedJacksonAndCompactOutput() {
        assertThat(normalizer.normalize(ParameterValueType.JSON, " { \"b\" : 2, \"a\" : [true] } "))
                .isEqualTo("{\"b\":2,\"a\":[true]}");
        assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.JSON, "{invalid}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTypeAndEmptyValueMustBeRejected() {
        assertThatThrownBy(() -> normalizer.normalize(null, "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize(ParameterValueType.STRING, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregateStatusChangeMustKeepIdentityAndValue() {
        SystemParameter parameter = SystemParameter.newParameter(ParameterScopeType.GLOBAL, "",
                "feature.order", ParameterValueType.BOOLEAN, "true", true, null);
        SystemParameter disabled = parameter.changeStatus(3L, false);
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.version()).isEqualTo(3L);
        assertThat(disabled.parameterKey()).isEqualTo("feature.order");
        assertThat(disabled.parameterValue()).isEqualTo("true");
    }
}
