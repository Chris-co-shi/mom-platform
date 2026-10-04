package io.github.chrisshi.mom.core.page;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PageQuery 不变量单元测试。
 *
 * <p>测试不启动 Spring 或数据库，只验证分页用例请求不会静默纠正非法输入。</p>
 */
class PageQueryTest {

    /** 合法请求必须完整保留调用方提供的 Params、页码和条数。 */
    @Test
    void shouldKeepValidPageQuery() {
        EmptyPageParams params = new EmptyPageParams();

        PageQuery<EmptyPageParams> query = new PageQuery<>(params, 2, 30);

        assertSame(params, query.params());
        assertEquals(2, query.pageNo());
        assertEquals(30, query.pageSize());
    }

    /** Params 缺失时必须拒绝，不能用 null 充当无条件查询。 */
    @Test
    void shouldRejectNullParams() {
        PageQueryValidationException exception = assertThrows(
                PageQueryValidationException.class,
                () -> new PageQuery<>(null, 1, 20));
        assertEquals("分页查询参数不能为空", exception.getMessage());
    }

    /** 页码不是正数时必须拒绝，不能静默改写为第一页。 */
    @Test
    void shouldRejectNonPositivePageNumber() {
        PageQueryValidationException exception = assertThrows(
                PageQueryValidationException.class,
                () -> new PageQuery<>(new EmptyPageParams(), 0, 20));
        assertEquals("页码必须大于等于 1", exception.getMessage());
    }

    /** 每页条数不是正数时必须拒绝，不能静默改写为默认值。 */
    @Test
    void shouldRejectNonPositivePageSize() {
        PageQueryValidationException exception = assertThrows(
                PageQueryValidationException.class,
                () -> new PageQuery<>(new EmptyPageParams(), 1, 0));
        assertEquals("每页条数必须大于等于 1", exception.getMessage());
    }

    /** 无业务过滤条件时使用的明确空参数类型。 */
    private record EmptyPageParams() {
    }
}
