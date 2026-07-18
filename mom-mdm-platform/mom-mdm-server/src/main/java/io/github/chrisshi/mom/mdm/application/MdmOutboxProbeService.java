package io.github.chrisshi.mom.mdm.application;

import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeEntity;
import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * MDM 领域写入与 Outbox 原子性技术验证服务。
 *
 * <p>该服务不是正式主数据业务能力，只用于 P01-S05 验证“业务表 INSERT + Outbox INSERT”共享同一个
 * PostgreSQL 本地事务。方法不会直接调用 RocketMQ；事务提交后由独立发布器读取 Outbox 并通过 Spring Cloud
 * Stream 发送，从而消除数据库与 Broker 同步双写窗口。</p>
 *
 * <p>类型不能声明为 {@code final}，因为当前使用类代理实现 {@link Transactional}；禁止为了代码风格重新加回
 * final 而破坏事务代理。</p>
 */
public class MdmOutboxProbeService {

    /** 正常消费技术事件类型。 */
    public static final String CREATED_EVENT_TYPE = "mdm.technical-probe.created";

    /** 用于验证 RocketMQ 重试和死信的故障事件类型。 */
    public static final String POISON_EVENT_TYPE = "mdm.technical-probe.poisoned";

    private static final String PRODUCER = "mom-mdm-server";

    private final MdmDataProbeService dataProbeService;
    private final OutboxAppender outboxAppender;
    private final Clock clock;

    /**
     * 创建 MDM Outbox 技术验证服务。
     *
     * @param dataProbeService 已有 PostgreSQL 技术记录服务
     * @param outboxAppender 强制当前事务内写入的 Outbox 端口
     * @param clock 平台 UTC 时钟
     */
    public MdmOutboxProbeService(
            MdmDataProbeService dataProbeService,
            OutboxAppender outboxAppender,
            Clock clock) {
        this.dataProbeService = Objects.requireNonNull(
                dataProbeService,
                "dataProbeService 不能为空");
        this.outboxAppender = Objects.requireNonNull(outboxAppender, "outboxAppender 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 在一个本地事务中创建技术业务记录并追加事件。
     *
     * @param probeKey 技术验证业务键
     * @param probeValue 技术验证值
     * @param correlationId 端到端关联标识
     * @param poisonEvent 是否生成消费者故意失败的技术事件
     * @return 包含业务记录和稳定事件身份的结果
     * @throws RuntimeException 业务 INSERT 或 Outbox INSERT 失败时抛出并整体回滚
     */
    @Transactional
    public MdmOutboxProbeResult create(
            String probeKey,
            String probeValue,
            String correlationId,
            boolean poisonEvent) {
        MdmDataProbeEntity probe = dataProbeService.create(probeKey, probeValue);
        String eventId = UUID.randomUUID().toString();
        String eventType = poisonEvent ? POISON_EVENT_TYPE : CREATED_EVENT_TYPE;
        EventEnvelope event = new EventEnvelope(
                eventId,
                eventType,
                1,
                "MdmDataProbe",
                probe.getId(),
                clock.instant(),
                PRODUCER,
                requireText(correlationId, "correlationId"),
                "{\"probeId\":\"" + probe.getId() + "\"}");
        outboxAppender.append(event);
        return new MdmOutboxProbeResult(probe.getId(), eventId, eventType);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    /**
     * 技术验证事务提交结果。
     *
     * @param probeId 业务技术记录 ID
     * @param eventId Outbox 事件唯一标识
     * @param eventType 事件类型
     */
    public record MdmOutboxProbeResult(
            String probeId,
            String eventId,
            String eventType) {
    }
}
