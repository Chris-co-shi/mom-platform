package io.github.chrisshi.mom.data.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.core.page.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * 平台分页请求与 MyBatis-Plus 分页对象之间的适配器。
 *
 * <p>该类型属于数据访问基础设施边界：Application 传入框架无关的 {@link PageQuery}，适配器在 SQL
 * 生成前执行配置化最大页大小校验，再创建 MyBatis-Plus {@link Page}；查询结果继续统一转换为
 * {@link PageResult}。适配器不解析业务过滤条件，也不感知 HTTP 或 {@code Result}。</p>
 *
 * <p>实例只保存启动时确定的正数上限，构造后不可变且线程安全。非法请求 fail-fast，不访问数据库，
 * 不再依赖分页插件静默截断请求。</p>
 */
public final class PageAdapter {
    private final long maxPageSize;

    /**
     * 创建配置化分页适配器。
     *
     * @param maxPageSize 服务允许的最大单页条数，必须大于零
     * @throws IllegalArgumentException 配置值不是正数时抛出
     */
    public PageAdapter(long maxPageSize) {
        if (maxPageSize <= 0) {
            throw new IllegalArgumentException("最大单页条数必须大于 0");
        }
        this.maxPageSize = maxPageSize;
    }

    /**
     * 校验分页请求并转换为 MyBatis-Plus 分页对象。
     *
     * @param pageQuery 已通过 Core 基础不变量校验的分页请求
     * @param <E> 数据库记录类型
     * @return 可交给 MyBatis-Plus Mapper 的分页对象
     * @throws PageQueryValidationException 每页条数超过服务配置上限时抛出
     */
    public <E> Page<E> toPage(PageQuery<?> pageQuery) {
        if (pageQuery.pageSize() > maxPageSize) {
            throw new PageQueryValidationException(
                    "pageSize", "每页条数不能超过 " + maxPageSize);
        }
        return Page.of(pageQuery.pageNo(), pageQuery.pageSize());
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为平台统一分页结果。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T> 记录类型
     * @return 与持久化框架解耦的统一分页结果
     */
    public <T> PageResult<T> toResult(IPage<T> page) {
        return new PageResult<>(
            List.copyOf(page.getRecords()),
            page.getCurrent(),
            page.getSize(),
            page.getTotal(),
            page.getPages()
        );
    }

    /**
     * 将 MyBatis-Plus 分页结果转换为平台统一分页结果，同时对记录进行类型映射。
     *
     * @param page   MyBatis-Plus 分页结果
     * @param mapper 记录类型映射函数
     * @param <S>    源记录类型（通常为 Entity）
     * @param <T>    目标记录类型（通常为 DTO）
     * @return 平台统一分页结果
     */
    public <S, T> PageResult<T> toResult(
        IPage<S> page,
        Function<? super S, T> mapper) {

        List<T> records = page.getRecords()
            .stream()
            .map(mapper)
            .toList();

        return new PageResult<>(
            records,
            page.getCurrent(),
            page.getSize(),
            page.getTotal(),
            page.getPages()
        );
    }
}
