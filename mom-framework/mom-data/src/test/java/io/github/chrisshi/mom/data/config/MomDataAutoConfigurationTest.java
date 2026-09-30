package io.github.chrisshi.mom.data.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MOM 数据自动配置的快速上下文测试。
 *
 * <p>测试只验证框架默认值和 application.yml 等价属性绑定，不连接数据库。上下文按测试独立创建并关闭，
 * 不共享可变状态；绑定非法值时由配置类 fail-fast。</p>
 */
class MomDataAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MomDataAutoConfiguration.class));

    /** 默认配置必须把分页插件单页上限固定为 200。 */
    @Test
    void shouldApplyDefaultMaximumPageSize() {
        contextRunner.run(context -> assertThat(pagination(context.getBean(MybatisPlusInterceptor.class)).getMaxLimit())
                .isEqualTo(MomDataPaginationProperties.DEFAULT_MAX_PAGE_SIZE));
    }

    /** 业务服务通过 application.yml 等价属性覆盖时，分页插件必须采用覆盖值。 */
    @Test
    void shouldApplyConfiguredMaximumPageSize() {
        contextRunner.withPropertyValues("mom.data.pagination.max-page-size=50")
                .run(context -> assertThat(pagination(context.getBean(MybatisPlusInterceptor.class)).getMaxLimit())
                        .isEqualTo(50L));
    }

    /** 非正数会关闭分页保护，因此配置对象必须在应用启动阶段直接拒绝。 */
    @Test
    void shouldRejectNonPositiveMaximumPageSize() {
        MomDataPaginationProperties properties = new MomDataPaginationProperties();

        assertThatThrownBy(() -> properties.setMaxPageSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须大于 0");
    }

    /** 从统一拦截器链中定位唯一分页插件，避免测试依赖插件添加顺序。 */
    private static PaginationInnerInterceptor pagination(MybatisPlusInterceptor interceptor) {
        return interceptor.getInterceptors().stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
