package io.github.chrisshi.mom.auth.controller.response;

import java.util.List;

public record OffsetPageResponse<T>(List<T> items, long total, int limit, long offset) {
    public OffsetPageResponse {
        items = List.copyOf(items);
    }
}
