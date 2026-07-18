package io.github.chrisshi.mom.outbox.application;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.persistence.JdbcOutboxRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * 业务事务内的 Outbox 追加端口。
 *
 * <p>该类型刻意拒绝在没有活动 Spring 本地事务时写入事件，防止调用方把“先提交业务、再插入 Outbox”误写成
 * 两个独立操作。正常用法是在显式 Application Service 的 {@code @Transactional} 方法中先完成领域写入，再调用
 * 本端口；任一步失败时由同一个 DataSourceTransactionManager 整体回滚。</p>
 */
public final class OutboxAppender {

    private final JdbcOutboxRepository repository;

    /**
     * 创建事务内 Outbox 追加端口。
     *
     * @param repository 当前服务 Schema 对应的 Outbox 仓储
     */
    public OutboxAppender(JdbcOutboxRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    /**
     * 在当前本地事务中追加事件。
     *
     * @param event 完整事件信封
     * @throws IllegalStateException 当前线程没有活动本地事务，或 INSERT 未影响一行时抛出
     * @throws RuntimeException 数据库约束或连接失败时透传并触发业务事务回滚
     */
    public void append(EventEnvelope event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox 事件必须在活动的本地数据库事务中追加");
        }
        int inserted = repository.append(event);
        if (inserted != 1) {
            throw new IllegalStateException("Outbox INSERT 未影响预期的一行记录");
        }
    }
}
