package io.github.chrisshi.mom.mdm.autoconfigure;

import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MDM PostgreSQL 技术验证事务服务自动配置。
 *
 * <p>只有 MyBatis-Plus 已成功创建 {@link SqlSessionFactory} 时才注册事务服务。HTTP 技术探针由独立的
 * 显式开关控制，默认不暴露；这样既能在真实数据库环境中复用事务服务进行集成测试，也能保证无数据库
 * Bootstrap 测试不会创建依赖 Mapper 的半成品对象。</p>
 */
@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
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
}
