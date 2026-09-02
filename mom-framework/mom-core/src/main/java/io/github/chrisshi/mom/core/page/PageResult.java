package io.github.chrisshi.mom.core.page;

import java.util.List;

public record PageResult<T>(
    List<T> records,
    long pageNo,
    long pageSize,
    long total,
    long totalPages
) {

    public PageResult{
        records = List.copyOf(records);
    }
}
