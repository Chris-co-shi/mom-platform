package io.github.chrisshi.mom.system.domain.preference;

/** 用户默认或单视图分页大小白名单。 */
public enum PageSize {
    TEN(10),
    TWENTY(20),
    FIFTY(50),
    ONE_HUNDRED(100);

    private final int value;

    PageSize(int value) {
        this.value = value;
    }

    /** 返回 HTTP 与持久化使用的整数值。 */
    public int value() {
        return value;
    }

    /** 严格解析分页白名单，不接受其他正整数。 */
    public static PageSize parse(Integer value) {
        for (PageSize pageSize : values()) {
            if (value != null && pageSize.value == value) {
                return pageSize;
            }
        }
        throw new PreferenceValidationException("invalid_page_size", "pageSize 只允许 10、20、50、100");
    }
}
