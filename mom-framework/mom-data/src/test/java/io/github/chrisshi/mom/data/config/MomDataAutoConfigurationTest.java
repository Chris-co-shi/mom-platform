package io.github.chrisshi.mom.data.config;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.data.page.PageAdapter;
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

    /** 默认配置必须让 PageAdapter 在 SQL 前拒绝超过 200 条的请求。 */
    @Test
    void shouldApplyDefaultMaximumPageSize() {
        contextRunner.run(context -> assertThatThrownBy(() -> context.getBean(PageAdapter.class)
                        .toPage(new PageQuery<>(new EmptyPageParams(), 1, 201)))
                .isInstanceOf(PageQueryValidationException.class)
                .hasMessage("每页条数不能超过 200"));
    }

    /** 业务服务通过 application.yml 等价属性覆盖时，PageAdapter 必须采用覆盖值。 */
    @Test
    void shouldApplyConfiguredMaximumPageSize() {
        contextRunner.withPropertyValues("mom.data.pagination.max-page-size=50")
                .run(context -> assertThat(context.getBean(PageAdapter.class)
                                .toPage(new PageQuery<>(new EmptyPageParams(), 1, 50)).getSize())
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

    /** 无业务过滤条件时使用的明确空参数类型。 */
    private record EmptyPageParams() {
    }
}
