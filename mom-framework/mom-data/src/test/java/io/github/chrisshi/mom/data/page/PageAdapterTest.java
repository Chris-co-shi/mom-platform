package io.github.chrisshi.mom.data.page;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置化 PageAdapter 单元测试。
 *
 * <p>测试不连接数据库，只验证在创建 MyBatis-Plus Page 前执行上限检查，确保超限请求没有 SQL 副作用。</p>
 */
class PageAdapterTest {

    /** 等于配置上限的分页请求必须被接受并保持原始元数据。 */
    @Test
    void shouldAcceptConfiguredMaximumPageSize() {
        PageAdapter adapter = new PageAdapter(50);

        var page = adapter.toPage(new PageQuery<>(new EmptyPageParams(), 2, 50));

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(50);
    }

    /** 超过配置上限时必须拒绝，不能截断成上限值。 */
    @Test
    void shouldRejectPageSizeAboveConfiguredMaximum() {
        PageAdapter adapter = new PageAdapter(50);

        assertThatThrownBy(() -> adapter.toPage(new PageQuery<>(new EmptyPageParams(), 1, 51)))
                .isInstanceOf(PageQueryValidationException.class)
                .hasMessage("每页条数不能超过 50");
    }

    /** 非正数框架配置会关闭保护，适配器构造时必须 fail-fast。 */
    @Test
    void shouldRejectNonPositiveMaximumConfiguration() {
        assertThatThrownBy(() -> new PageAdapter(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("最大单页条数必须大于 0");
    }

    /** 无业务过滤条件时使用的明确空参数类型。 */
    private record EmptyPageParams() {
    }
}
