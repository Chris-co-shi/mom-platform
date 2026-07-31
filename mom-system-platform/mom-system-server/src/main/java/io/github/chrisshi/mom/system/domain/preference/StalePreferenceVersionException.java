package io.github.chrisshi.mom.system.domain.preference;

/** 用户偏好或视图设置的乐观版本已陈旧；Web 稳定映射为 409 stale_version。 */
public final class StalePreferenceVersionException extends RuntimeException {
    public StalePreferenceVersionException() {
        super("偏好已被其他请求修改，请重新读取");
    }
}
