package io.github.chrisshi.mom.mdm.autoconfigure;

import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import io.github.chrisshi.mom.mdm.application.MdmOutboxProbeService;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * MDM PostgreSQL 技术验证事务服务自动配置。
 *
 * <p>只有 MyBatis-Plus 已成功创建 {@link SqlSessionFactory} 时才注册事务服务。HTTP 技术探针由独立的
 * 显式开关控制，默认不暴露；这样既能在真实数据库环境中复用事务服务进行集成测试，也能保证无数据库
 * Bootstrap 测试不会创建依赖 Mapper 的半成品对象。</p>
 *
 * <p>Outbox 技术服务还要求 {@link OutboxAppender} 已由 Framework 创建。它与普通数据探针共享当前服务
 * 唯一 DataSource 和事务管理器，不创建第二连接池。</p>
 */
@AutoConfiguration(afterName = {
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "io.github.chrisshi.mom.outbox.autoconfigure.MomOutboxAutoConfiguration"
})
@ConditionalOnBean(SqlSessionFactory.class)
public class MdmDataProbeAutoConfiguration {

    /**
     * 创建 MDM 数据技术验证事务服务。
     *
     * @param mapper MyBatis-Plus 扫描得到的技术验证 Mapper
     * @return 显式事务服务
     */
    @Bean
    @ConditionalOnMissingBean(MdmDataProbeService.class)
    MdmDataProbeService mdmDataProbeService(MdmDataProbeMapper mapper) {
        return new MdmDataProbeService(mapper);
    }

    /**
     * 创建 MDM 业务写入与 Outbox 原子性验证服务。
     *
     * @param dataProbeService 已有技术业务事务服务
     * @param outboxAppender 当前事务内 Outbox 追加端口
     * @param clock 平台 UTC 时钟
     * @return P01-S05 技术验证服务
     */
    @Bean
    @ConditionalOnBean(OutboxAppender.class)
    @ConditionalOnMissingBean(MdmOutboxProbeService.class)
    MdmOutboxProbeService mdmOutboxProbeService(
            MdmDataProbeService dataProbeService,
            OutboxAppender outboxAppender,
            Clock clock) {
        return new MdmOutboxProbeService(dataProbeService, outboxAppender, clock);
    }
}
