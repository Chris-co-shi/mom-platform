package io.github.chrisshi.mom.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbox 发布器配置。
 *
 * <p>配置只控制数据库领取和重试节奏，不修改 Spring Cloud Stream 或 RocketMQ 的 Broker 配置。默认关闭
 * 发布器，使依赖 {@code mom-outbox} 但尚未创建 Outbox 表的服务仍可启动；需要可靠发布的服务必须显式启用。
 * 每次领取数量和租约应结合 Hikari 连接池、Broker 延迟与应用副本数进行压测后调整。</p>
 */
@ConfigurationProperties("mom.outbox.publisher")
public class OutboxPublisherProperties {

    private boolean enabled;
    private String bindingName = "momDomainEvents-out-0";
    private long fixedDelayMillis = 1000;
    private int batchSize = 20;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private int maxAttempts = 8;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }

    public long getFixedDelayMillis() {
        return fixedDelayMillis;
    }

    public void setFixedDelayMillis(long fixedDelayMillis) {
        this.fixedDelayMillis = fixedDelayMillis;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    /**
     * 校验发布器配置，避免零批次、无效租约或重试参数导致忙循环和重复领取。
     *
     * @throws IllegalArgumentException 任一配置不满足最小约束时抛出
     */
    public void validate() {
        if (bindingName == null || bindingName.isBlank()) {
            throw new IllegalArgumentException("mom.outbox.publisher.binding-name 不能为空");
        }
        if (fixedDelayMillis < 100) {
            throw new IllegalArgumentException("fixed-delay-millis 不能小于 100");
        }
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("batch-size 必须位于 1 到 500 之间");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("lease-duration 必须为正数");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("max-attempts 必须大于零");
        }
        if (initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initial-backoff 必须为正数");
        }
        if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("max-backoff 不能小于 initial-backoff");
        }
    }
}
