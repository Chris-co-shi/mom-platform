package io.github.chrisshi.mom.core.page;

public record PageQuery<T>(
    T params,
    long pageNo,
    long pageSize
) {

    public PageQuery {
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
    }
}
