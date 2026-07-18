package io.github.chrisshi.mom.mdm.autoconfigure;

import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
import io.github.chrisshi.mom.mdm.interfaces.rest.internal.MdmDataProbeController;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MDM PostgreSQL 技术探针自动配置。
 *
 * <p>只有 MyBatis-Plus 已成功创建 {@link SqlSessionFactory} 时才注册事务服务和 Controller。这样既能
 * 在真实数据库环境中启用 P01-S04 验证，也能保持现有“不连接数据库的启动边界测试”继续运行，避免
 * 因测试主动排除 DataSource 自动配置而出现缺失 Mapper 的假失败。</p>
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

    /**
     * 创建 MDM 数据技术验证内部 Controller。
     *
     * @param service 技术验证事务服务
     * @return 仅用于 P01-S04 的内部接口
     */
    @Bean
    @ConditionalOnMissingBean(MdmDataProbeController.class)
    MdmDataProbeController mdmDataProbeController(MdmDataProbeService service) {
        return new MdmDataProbeController(service);
    }
}
