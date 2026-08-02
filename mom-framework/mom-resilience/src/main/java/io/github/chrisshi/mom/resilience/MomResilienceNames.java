package io.github.chrisshi.mom.resilience;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * MOM Resilience Profile 与实例的稳定命名规则。
 *
 * <p>名称是配置和 Micrometer 指标的低基数标识，不包含 URL、参数、Factory、用户或业务单号。Framework
 * 只冻结名称结构，不冻结 CircuitBreaker/TimeLimiter/Bulkhead 参数值；业务实例配置可以覆盖命名 Profile，
 * 命名 Profile 再继承默认 Profile。类无状态且线程安全。</p>
 */
public final class MomResilienceNames {

    /** Framework 合理默认 Profile，不代表冻结参数。 */
    public static final String DEFAULT_PROFILE = "default";
    /** System 只读权威查询 Profile。 */
    public static final String SYSTEM_QUERY_PROFILE = "system-query";

    private static final Pattern METHOD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,127}");

    private MomResilienceNames() {
    }

    /**
     * 生成 Spring Cloud OpenFeign 使用的稳定 CircuitBreaker 实例名。
     *
     * @param clientType Feign Client 接口类型
     * @param methodName Java 方法名
     * @return {@code ClientSimpleName_methodName} 格式的低基数名称
     * @throws IllegalArgumentException 方法名为空或包含不安全字符时抛出
     */
    public static String feignInstance(Class<?> clientType, String methodName) {
        Objects.requireNonNull(clientType, "Feign Client 类型不能为空");
        if (methodName == null || !METHOD_NAME.matcher(methodName).matches()) {
            throw new IllegalArgumentException("Feign 方法名不符合 Resilience 实例命名规则");
        }
        return clientType.getSimpleName() + "_" + methodName;
    }
}
