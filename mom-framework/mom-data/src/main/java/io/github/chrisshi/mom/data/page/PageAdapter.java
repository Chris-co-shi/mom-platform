package io.github.chrisshi.mom.data.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * @author 史偕成
 * @date 2026/09/02 17:10
 **/
public final class PageAdapter {

    private PageAdapter() {
    }

    public static <E> Page<E> toPage(PageQuery<?> pageQuery) {
        return Page.of(pageQuery.pageNo(), pageQuery.pageSize());
    }

    public static <T> PageResult<T> toResult(IPage<T> page) {
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
    public static <S, T> PageResult<T> toResult(
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
