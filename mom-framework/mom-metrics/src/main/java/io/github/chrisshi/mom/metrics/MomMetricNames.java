package io.github.chrisshi.mom.metrics;

/**
 * MOM 平台运行指标名称常量。
 *
 * <p>该类型只定义跨基础设施模块共享的稳定 Micrometer 名称，不持有注册表，也不创建指标。指标名称使用
 * 点分隔形式，由 Prometheus Registry 转换为下划线形式。业务 ID、用户 ID、Trace ID 等高基数字段不得
 * 出现在这些指标的标签中。</p>
 */
public final class MomMetricNames {

    /** Gateway Redis 限流判定次数。 */
    public static final String GATEWAY_RATE_LIMIT_REQUESTS = "mom.gateway.rate.limit.requests";

    /** Outbox 单条消息发布结果次数。 */
    public static final String OUTBOX_PUBLISH_RESULTS = "mom.outbox.publish.results";

    /** Inbox 消费幂等处理结果次数。 */
    public static final String INBOX_PROCESS_RESULTS = "mom.inbox.process.results";

    private MomMetricNames() {
    }
}
