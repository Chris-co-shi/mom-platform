package io.github.chrisshi.mom.system.domain.preference;

/** 用户显示主题白名单；值只影响客户端外观。 */
public enum ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** 严格解析主题枚举，不接受大小写变体。 */
    public static ThemeMode parse(String value) {
        try {
            return ThemeMode.valueOf(value);
        } catch (RuntimeException exception) {
            throw new PreferenceValidationException("invalid_theme", "不支持的 themeMode", exception);
        }
    }
}
