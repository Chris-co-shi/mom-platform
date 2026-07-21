package io.github.chrisshi.mom.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MOM 数据审计自动填充配置。
 *
 * <p>生产、开发和测试默认都启用审计，并在缺少 Actor 时失败。关闭或放宽只允许用于经过评审的迁移、
 * 修复或兼容场景，不能作为普通业务写入的绕过开关。</p>
 */
@ConfigurationProperties(prefix = "mom.data.audit")
public class MomDataAuditProperties {

    private boolean enabled = true;
    private boolean failOnMissingActor = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFailOnMissingActor() { return failOnMissingActor; }
    public void setFailOnMissingActor(boolean failOnMissingActor) { this.failOnMissingActor = failOnMissingActor; }
}
