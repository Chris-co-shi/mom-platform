package io.github.chrisshi.mom.integration.application;

import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantRequest;
import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantResponse;
import org.apache.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * Integration Seata AT 技术分支参与者。
 *
 * <p>该服务只用于验证同步短事务中的 AT 分支注册、XID 传播、PostgreSQL Undo Log 和全局回滚。它不承载
 * Integration Hub 的正式外部系统编排，也不允许在事务内等待消息、人工、设备或第三方回调。</p>
 *
 * <p>方法使用 Spring 本地事务，使业务 INSERT 与 Seata {@code undo_log} 生成位于同一个数据库事务中。
 * 当故障注入触发异常时，本地事务直接回滚；当上游稍后决定全局回滚时，TC 会要求该 RM 使用 Undo Log
 * 补偿已经提交的分支。服务要求线程中存在 XID，缺失时 fail-fast，防止技术接口在未加入全局事务时静默写入。</p>
 */
@Service
public class IntegrationSeataAtParticipantService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * 创建 Integration AT 技术参与者。
     *
     * @param jdbcTemplate 当前服务唯一权威 DataSource 对应的 JDBC 模板
     * @param clock 平台 UTC 时钟
     */
    public IntegrationSeataAtParticipantService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 在当前 Seata 全局事务下提交一个 Integration 本地 AT 分支。
     *
     * @param request 技术事务键、写入值和故障注入参数
     * @return 当前分支观察到的 XID 和成功状态
     * @throws IllegalStateException 当前线程没有 XID、INSERT 结果异常或主动故障注入时抛出；本地事务回滚
     */
    @Transactional
    public IntegrationSeataAtParticipantResponse participate(
            IntegrationSeataAtParticipantRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        String transactionKey = requireText(request.transactionKey(), "transactionKey");
        String participantValue = requireText(request.participantValue(), "participantValue");
        String xid = requireActiveXid();

        int inserted = jdbcTemplate.update("""
                        INSERT INTO technical_seata_at_participant (
                            transaction_key,
                            participant_value,
                            xid,
                            created_at
                        ) VALUES (?, ?, ?, ?)
                        """,
                transactionKey,
                participantValue,
                xid,
                Timestamp.from(clock.instant()));
        if (inserted != 1) {
            throw new IllegalStateException("Integration AT 技术参与者未插入预期的一行记录");
        }
        if (request.failParticipant()) {
            throw new IllegalStateException("P01-S06 主动触发 Integration AT 分支本地回滚");
        }
        return new IntegrationSeataAtParticipantResponse(
                transactionKey,
                xid,
                "RECORDED");
    }

    private static String requireActiveXid() {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("Integration AT 技术参与者未收到 Seata XID");
        }
        return xid;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
