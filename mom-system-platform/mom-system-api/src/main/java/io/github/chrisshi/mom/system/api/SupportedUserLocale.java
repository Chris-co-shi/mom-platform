package io.github.chrisshi.mom.system.api;

/** System 用户显示偏好的稳定 Locale 契约；枚举值严格对应 BCP 47 Tag。 */
public enum SupportedUserLocale {
    ZH_CN("zh-CN"),
    EN_US("en-US");

    private final String tag;

    SupportedUserLocale(String tag) {
        this.tag = tag;
    }

    /** 返回跨客户端传输的严格 BCP 47 Tag。 */
    public String tag() {
        return tag;
    }
}
