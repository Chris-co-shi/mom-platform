package io.github.chrisshi.mom.system.domain.preference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S16 显示偏好、视图类型模型、Secret 防护和 Reset 的纯 Domain 测试。 */
class UserPreferenceRulesTest {

    @Test
    void localeTimezoneThemeDensityAndPageSizeMustBeStrict() {
        assertThat(SupportedLocale.parse("zh-CN")).isEqualTo(SupportedLocale.ZH_CN);
        assertThat(SupportedLocale.parse("en-US")).isEqualTo(SupportedLocale.EN_US);
        assertThat(new DisplayTimezone("Asia/Shanghai").value()).isEqualTo("Asia/Shanghai");
        assertThat(new DisplayTimezone("Europe/Prague").value()).isEqualTo("Europe/Prague");
        assertThat(new DisplayTimezone("UTC").value()).isEqualTo("UTC");
        assertThat(ThemeMode.parse("SYSTEM")).isEqualTo(ThemeMode.SYSTEM);
        assertThat(Density.parse("COMPACT")).isEqualTo(Density.COMPACT);
        assertThat(PageSize.parse(100).value()).isEqualTo(100);

        assertCode(() -> SupportedLocale.parse("zh_CN"), "unsupported_locale");
        assertCode(() -> SupportedLocale.parse("zh"), "unsupported_locale");
        assertCode(() -> SupportedLocale.parse("en-us"), "unsupported_locale");
        assertCode(() -> new DisplayTimezone("GMT+8"), "invalid_timezone");
        assertCode(() -> new DisplayTimezone("+08:00"), "invalid_timezone");
        assertCode(() -> new DisplayTimezone("Unknown/Zone"), "invalid_timezone");
        assertCode(() -> ThemeMode.parse("dark"), "invalid_theme");
        assertCode(() -> Density.parse("DENSE"), "invalid_density");
        assertCode(() -> PageSize.parse(25), "invalid_page_size");
    }

    @Test
    void applicationViewAndFieldKeysMustRejectSecretLikeSegments() {
        assertThat(PreferenceRules.requireApplicationCode("mom-admin")).isEqualTo("mom-admin");
        assertThat(PreferenceRules.requireViewKey("iam.users.list")).isEqualTo("iam.users.list");
        assertThat(PreferenceRules.requireFieldKey("display-name", "invalid_column_setting"))
                .isEqualTo("display-name");
        assertCode(() -> PreferenceRules.requireApplicationCode("Mom-Admin"), "invalid_application_code");
        assertCode(() -> PreferenceRules.requireViewKey("users/List"), "invalid_view_key");
        assertCode(() -> PreferenceRules.requireViewKey("users.access-token"), "forbidden_preference_key");
        assertCode(() -> PreferenceRules.requireFieldKey("client-secret", "invalid_filter_setting"),
                "forbidden_preference_key");
    }

    @Test
    void columnsMustEnforceCountUniqueOrderAndWidth() {
        ColumnSetting first = column("name", 0, 200);
        ColumnSetting second = column("status", 1, null);
        PreferenceRules.validateView(List.of(first, second), List.of(), List.of());
        assertCode(() -> PreferenceRules.validateView(List.of(first, column("name", 1, null)), List.of(), List.of()),
                "invalid_column_setting");
        assertCode(() -> PreferenceRules.validateView(List.of(first, column("status", 0, null)), List.of(), List.of()),
                "invalid_column_setting");
        assertCode(() -> new ColumnSetting("name", true, 0, 49, ColumnSetting.Pinned.NONE),
                "invalid_column_setting");
        List<ColumnSetting> tooMany = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> column("field-" + index, index, null)).toList();
        assertCode(() -> PreferenceRules.validateView(tooMany, List.of(), List.of()), "invalid_column_setting");
    }

    @Test
    void sortsMustEnforceMaximumUniqueAndContinuousPriority() {
        SortSetting first = sort("name", 0);
        SortSetting second = sort("status", 1);
        PreferenceRules.validateView(List.of(), List.of(first, second), List.of());
        assertCode(() -> PreferenceRules.validateView(List.of(), List.of(first, sort("name", 1)), List.of()),
                "invalid_sort_setting");
        assertCode(() -> PreferenceRules.validateView(List.of(), List.of(first, sort("status", 2)), List.of()),
                "invalid_sort_setting");
        assertCode(() -> PreferenceRules.validateView(List.of(), List.of(
                first, second, sort("created-at", 2), sort("updated-at", 3)), List.of()),
                "invalid_sort_setting");
    }

    @Test
    void filtersMustEnforceOperatorTypeCountScalarAndSensitiveValue() {
        FilterSetting between = new FilterSetting("created-at", FilterSetting.Operator.BETWEEN,
                FilterSetting.ValueType.DATE, List.of("2026-01-01", "2026-01-31"));
        FilterSetting enabled = new FilterSetting("enabled", FilterSetting.Operator.EQ,
                FilterSetting.ValueType.BOOLEAN, List.of("true"));
        PreferenceRules.validateView(List.of(), List.of(), List.of(between, enabled));
        assertCode(() -> new FilterSetting("name", FilterSetting.Operator.BETWEEN,
                FilterSetting.ValueType.STRING, List.of("one")), "invalid_filter_setting");
        assertCode(() -> new FilterSetting("enabled", FilterSetting.Operator.EQ,
                FilterSetting.ValueType.BOOLEAN, List.of("yes")), "invalid_filter_setting");
        assertCode(() -> new FilterSetting("created-at", FilterSetting.Operator.EQ,
                FilterSetting.ValueType.INSTANT, List.of("not-instant")), "invalid_filter_setting");
        assertCode(() -> new FilterSetting("name", FilterSetting.Operator.EQ,
                FilterSetting.ValueType.STRING, List.of("Bearer secret-value")), "invalid_filter_setting");
        List<FilterSetting> tooMany = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> new FilterSetting("field-" + index, FilterSetting.Operator.IS_NULL,
                        FilterSetting.ValueType.STRING, List.of())).toList();
        assertCode(() -> PreferenceRules.validateView(List.of(), List.of(), tooMany), "invalid_filter_setting");
    }

    @Test
    void payloadSizeAndViewResetMustBeBoundedAndNonDeleting() {
        PreferenceRules.requirePayloadSize("[]", "[]", "[]");
        assertCode(() -> PreferenceRules.requirePayloadSize("x".repeat(PreferenceRules.MAX_PAYLOAD_BYTES + 1)),
                "payload_too_large");
        UserViewSetting setting = UserViewSetting.create("100", "mom-admin", "iam.users.list", 2,
                List.of(column("name", 0, 200)), List.of(sort("name", 0)), List.of(), PageSize.FIFTY);
        UserViewSetting reset = setting.reset(0);
        assertThat(reset.id()).isNull();
        assertThat(reset.enabled()).isFalse();
        assertThat(reset.columns()).isEmpty();
        assertThat(reset.sorts()).isEmpty();
        assertThat(reset.filters()).isEmpty();
        assertThat(reset.pageSize()).isNull();
    }

    @Test
    void nullOverridesMustRemainExplicitAndVersioned() {
        UserPreference preference = UserPreference.create("100", SupportedLocale.EN_US,
                new DisplayTimezone("Asia/Tokyo"), ThemeMode.DARK, Density.COMPACT, PageSize.FIFTY);
        UserPreference reset = preference.replace(0, null, null, null, null, null);
        assertThat(reset.locale()).isNull();
        assertThat(reset.displayTimezone()).isNull();
        assertThat(reset.themeMode()).isNull();
        assertThat(reset.density()).isNull();
        assertThat(reset.pageSize()).isNull();
        assertThatThrownBy(() -> preference.replace(1, null, null, null, null, null))
                .isInstanceOf(StalePreferenceVersionException.class);
    }

    private static ColumnSetting column(String key, int order, Integer width) {
        return new ColumnSetting(key, true, order, width, ColumnSetting.Pinned.NONE);
    }

    private static SortSetting sort(String key, int priority) {
        return new SortSetting(key, SortSetting.Direction.ASC, priority);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable).isInstanceOf(PreferenceValidationException.class)
                .extracting(exception -> ((PreferenceValidationException) exception).code())
                .isEqualTo(code);
    }
}
