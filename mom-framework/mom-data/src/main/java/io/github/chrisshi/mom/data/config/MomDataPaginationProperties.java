package io.github.chrisshi.mom.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MOM 数据框架的统一分页保护配置。
 *
 * <p>该配置由各业务服务通过 {@code application.yml} 的
 * {@code mom.data.pagination.max-page-size} 覆盖，默认最多返回 200 行。配置在应用启动时完成绑定，此后只读；
 * 非正数会使服务启动失败，避免关闭分页保护。具体业务仍负责稳定排序和过滤条件。</p>
 */
@ConfigurationProperties("mom.data.pagination")
public class MomDataPaginationProperties {
    /** 平台默认单页最大记录数。 */
    public static final long DEFAULT_MAX_PAGE_SIZE = 200L;

    private long maxPageSize = DEFAULT_MAX_PAGE_SIZE;

    /**
     * @return MyBatis-Plus 执行 SQL 前允许的最大单页记录数
     */
    public long getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置服务级分页上限；仅由 Spring Boot 配置绑定在启动阶段调用。
     *
     * @param maxPageSize 必须大于零的最大单页记录数
     * @throws IllegalArgumentException 配置为零或负数时抛出并阻止服务启动
     */
    public void setMaxPageSize(long maxPageSize) {
        if (maxPageSize <= 0) {
            throw new IllegalArgumentException("mom.data.pagination.max-page-size 必须大于 0");
        }
        this.maxPageSize = maxPageSize;
    }
}
