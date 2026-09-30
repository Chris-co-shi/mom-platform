package io.github.chrisshi.mom.core.page;

/**
 * 平台统一分页用例请求。
 *
 * <p>该类型位于 Core 边界，只表达业务查询参数、页码和每页条数，不依赖 HTTP、Spring 或 MyBatis。
 * Controller 与 Application 使用同一个强类型 {@code PageQuery<XxxPageParams>} 传递分页用例，避免过滤条件
 * 以裸参数形式在分层之间扩散。最大页大小属于运行时配置，由数据访问层的 PageAdapter 在执行 SQL 前校验。</p>
 *
 * <p>记录类型不可变且线程安全；构造失败不会产生数据库或其他外部基础设施副作用。</p>
 *
 * @param params 不能为空的强类型业务查询参数；没有过滤条件时传入明确的空参数对象
 * @param pageNo 从 1 开始的页码
 * @param pageSize 大于零的每页条数，配置化最大值由 PageAdapter 校验
 * @param <T> 业务查询参数类型
 */
public record PageQuery<T>(
    T params,
    long pageNo,
    long pageSize
) {

    /**
     * 校验与运行环境无关的分页不变量，不对非法输入做静默默认或纠正。
     *
     * @throws PageQueryValidationException 参数对象为空、页码或每页条数不是正数时抛出
     */
    public PageQuery {
        if (params == null) {
            throw new PageQueryValidationException("params", "分页查询参数不能为空");
        }
        if (pageNo < 1) {
            throw new PageQueryValidationException("pageNo", "页码必须大于等于 1");
        }
        if (pageSize < 1) {
            throw new PageQueryValidationException("pageSize", "每页条数必须大于等于 1");
        }
    }
}
