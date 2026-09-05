package io.github.chrisshi.mom.core.page;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 框架无关的分页结果。
 *
 * <p>业务模块应优先复用该类型，不再为相同的 records/total/pageNo/pageSize
 * 语义创建一层只做字段复制的 PageView/PageResponse。</p>
 */
public record PageResult<T>(
    List<T> records,
    long pageNo,
    long pageSize,
    long total,
    long totalPages
) {

    public PageResult {
        records = List.copyOf(records);
    }

    /**
     * 保留分页元数据，只转换记录类型，供 Application View 到 HTTP Response 的边界映射使用。
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new PageResult<>(
            records.stream().map(mapper).toList(),
            pageNo,
            pageSize,
            total,
            totalPages
        );
    }
}
