package io.github.chrisshi.mom.cache.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 表示一个携带 Region 与 Global/Factory Scope 的类型化缓存 Key。
 *
 * <p>该对象只接受已经完成敏感信息摘要或安全编码的 Subject，禁止把姓名、Token、原始幂等键等敏感值直接
 * 放入 Key。物理 Key 在调用 {@link #build(String)} 时一次性生成，不依赖线程上下文，因此不会发生
 * Factory 泄漏。实例不可变且线程安全。</p>
 *
 * @param region  缓存区域
 * @param scope   Global 或权威验证后的 Factory Scope
 * @param subject 已安全编码的缓存主体
 * @param <T>     缓存值类型
 */
public record CacheEntryKey<T>(CacheRegion<T> region, CacheScope scope, String subject) {

    private static final Pattern ENVIRONMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Pattern SUBJECT = Pattern.compile("[A-Za-z0-9._-]+(?::[A-Za-z0-9._-]+)*");

    public CacheEntryKey {
        Objects.requireNonNull(region, "CacheRegion 不能为空");
        Objects.requireNonNull(scope, "CacheScope 不能为空");
        Objects.requireNonNull(subject, "缓存 Subject 不能为空");
        if (subject.length() > 256 || !SUBJECT.matcher(subject).matches()) {
            throw new IllegalArgumentException("缓存 Subject 必须先完成安全编码或摘要");
        }
    }

    /**
     * 创建类型化缓存 Key。
     *
     * @param region 缓存区域
     * @param scope Global 或 Factory Scope
     * @param subject 已安全编码的主体
     * @param <T> 缓存值类型
     * @return 不可变缓存 Key
     */
    public static <T> CacheEntryKey<T> of(CacheRegion<T> region, CacheScope scope, String subject) {
        return new CacheEntryKey<>(region, scope, subject);
    }

    /**
     * 按 MOM Key 规范构建完整物理 Key。
     *
     * @param environment 已规范化的部署环境名，例如 prod
     * @return 包含 Environment、Scope、Context、版本、能力和主体的物理 Key
     * @throws IllegalArgumentException 当环境名不能安全进入 Key 时抛出
     */
    public String build(String environment) {
        Objects.requireNonNull(environment, "缓存环境名不能为空");
        if (!ENVIRONMENT.matcher(environment).matches()) {
            throw new IllegalArgumentException("缓存环境名必须是可安全进入 Key 的小写名称");
        }
        return "mom:%s:%s:%s:cache:v%d:%s:%s".formatted(
                environment,
                scope.value(),
                region.boundedContext(),
                region.keyVersion(),
                region.capability(),
                subject
        );
    }
}
