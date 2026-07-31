package io.github.chrisshi.mom.system.domain.preference;

/** 用户页面显示密度白名单。 */
public enum Density {
    COMFORTABLE,
    COMPACT;

    /** 严格解析显示密度枚举。 */
    public static Density parse(String value) {
        try {
            return Density.valueOf(value);
        } catch (RuntimeException exception) {
            throw new PreferenceValidationException("invalid_density", "不支持的 density", exception);
        }
    }
}
