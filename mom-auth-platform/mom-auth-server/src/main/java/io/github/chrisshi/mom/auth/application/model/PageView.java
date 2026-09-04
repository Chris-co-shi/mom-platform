package io.github.chrisshi.mom.auth.application.model;

import java.util.List;

public record PageView<T>(List<T> items, long total) {
    public PageView {
        items = List.copyOf(items);
    }
}
