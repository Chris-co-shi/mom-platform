package io.github.chrisshi.mom.system.domain.preference;

/**
 * System V1 严格支持的用户 Locale 值对象。
 *
 * <p>Domain 只接受精确 BCP 47 Tag，不做 Alias、大小写或下划线规范化；该选择避免服务端把浏览器候选
 * 静默保存为永久偏好。枚举不依赖 Spring/Web，解析失败立即拒绝。</p>
 */
public enum SupportedLocale {
    ZH_CN("zh-CN"),
    EN_US("en-US");

    private final String tag;

    SupportedLocale(String tag) {
        this.tag = tag;
    }

    /** 返回持久化与 HTTP 使用的严格 Tag。 */
    public String tag() {
        return tag;
    }

    /** 严格解析白名单 Tag；不接受任何别名或规范化变体。 */
    public static SupportedLocale parse(String value) {
        for (SupportedLocale locale : values()) {
            if (locale.tag.equals(value)) {
                return locale;
            }
        }
        throw new PreferenceValidationException("unsupported_locale", "不支持的 Locale");
    }
}
