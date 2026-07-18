package io.github.chrisshi.mom.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Redis 幂等 Key 工厂。
 *
 * <p>Key 结构固定为 {@code mom:{environment}:{application}:idempotency:{scope}:{sha256}}。
 * 原始请求幂等值只参与 SHA-256 摘要计算，不直接进入 Redis Key，避免订单号、批次号、外部单号
 * 或其他敏感业务标识暴露在监控、慢日志和运维命令中。</p>
 *
 * <p>该类型是无状态且线程安全的，可以被单例复用。</p>
 */
public final class RedisIdempotencyKeyFactory {

    private static final int MAX_SCOPE_LENGTH = 96;

    private final String environment;
    private final String applicationName;

    /**
     * 创建幂等 Key 工厂。
     *
     * @param environment 部署环境名称
     * @param applicationName 当前应用名称
     */
    public RedisIdempotencyKeyFactory(String environment, String applicationName) {
        this.environment = normalizeSegment(environment, "environment");
        this.applicationName = normalizeSegment(applicationName, "applicationName");
    }

    /**
     * 根据业务作用域和原始幂等值生成不可逆的 Redis Key。
     *
     * @param scope 业务动作作用域
     * @param requestKey 原始幂等值
     * @return 包含统一命名空间和 SHA-256 摘要的 Redis Key
     */
    public String create(String scope, String requestKey) {
        String normalizedScope = normalizeSegment(scope, "scope");
        if (normalizedScope.length() > MAX_SCOPE_LENGTH) {
            throw new IllegalArgumentException("幂等 scope 长度不能超过 " + MAX_SCOPE_LENGTH);
        }
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException("幂等 requestKey 不能为空");
        }
        return "mom:%s:%s:idempotency:%s:%s".formatted(
                environment,
                applicationName,
                normalizedScope,
                sha256(requestKey.trim()));
    }

    /**
     * 将命名空间片段限制在可预测字符集合中，防止冒号、空格或控制字符破坏 Key 分段语义。
     */
    private static String normalizeSegment(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " 不能为空");
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return normalized;
    }

    /**
     * 计算稳定的 SHA-256 十六进制摘要。JDK 必须提供该算法，缺失时属于运行环境不可恢复错误。
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256，无法生成幂等 Key", exception);
        }
    }
}
