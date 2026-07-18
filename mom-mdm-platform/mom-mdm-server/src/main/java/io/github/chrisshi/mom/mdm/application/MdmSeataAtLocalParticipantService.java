package io.github.chrisshi.mom.mdm.application;

import org.apache.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;

/**
 * MDM Seata AT 发起方中的本地数据库分支。
 *
 * <p>该类型把 MDM 本地 INSERT 放在独立的 Spring 事务代理中，使全局事务发起逻辑与本地资源分支边界
 * 明确分离。它只写当前 MDM 数据库，不调用远端服务，不创建第二 DataSource，也不承担 Seata 全局事务的
 * 开始或结束。</p>
 *
 * <p>调用时必须已经由外层 {@code @GlobalTransactional} 建立 XID。若没有 XID，则拒绝写入，避免技术 PoC
 * 退化成普通本地事务并给出虚假的兼容性结论。</p>
 */
@Service
public class MdmSeataAtLocalParticipantService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    /**
     * 创建 MDM 本地 AT 分支服务。
     *
     * @param jdbcTemplate 当前 MDM 唯一权威 DataSource 对应的 JDBC 模板
     * @param clock 平台 UTC 时钟
     */
    public MdmSeataAtLocalParticipantService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 在当前全局事务中提交 MDM 本地 AT 分支。
     *
     * @param transactionKey 本次短事务技术键
     * @param coordinatorValue MDM 发起方技术验证值
     * @return 当前线程观察到的 Seata XID
     * @throws IllegalStateException 未加入全局事务或 INSERT 结果异常时抛出并回滚本地事务
     */
    @Transactional
    public String record(String transactionKey, String coordinatorValue) {
        String normalizedKey = requireText(transactionKey, "transactionKey");
        String normalizedValue = requireText(coordinatorValue, "coordinatorValue");
        String xid = requireActiveXid();

        int inserted = jdbcTemplate.update("""
                        INSERT INTO technical_seata_at_coordinator (
                            transaction_key,
                            coordinator_value,
                            xid,
                            created_at
                        ) VALUES (?, ?, ?, ?)
                        """,
                normalizedKey,
                normalizedValue,
                xid,
                Timestamp.from(clock.instant()));
        if (inserted != 1) {
            throw new IllegalStateException("MDM AT 技术发起方未插入预期的一行记录");
        }
        return xid;
    }

    private static String requireActiveXid() {
        String xid = RootContext.getXID();
        if (xid == null || xid.isBlank()) {
            throw new IllegalStateException("MDM AT 本地分支执行前没有活动 Seata XID");
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
