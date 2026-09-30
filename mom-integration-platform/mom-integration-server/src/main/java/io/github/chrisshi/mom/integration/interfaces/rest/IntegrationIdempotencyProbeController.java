package io.github.chrisshi.mom.integration.interfaces.rest;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.IdempotencyHeaders;
import io.github.chrisshi.mom.idempotency.IdempotencyAcquireResult;
import io.github.chrisshi.mom.idempotency.IdempotencyAcquireStatus;
import io.github.chrisshi.mom.idempotency.IdempotencyGuard;
import io.github.chrisshi.mom.idempotency.IdempotencyUnavailableException;
import io.github.chrisshi.mom.idempotency.RedisIdempotencyProperties;
import io.github.chrisshi.mom.integration.api.probe.IdempotencyProbeResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Integration 服务的 Redis 幂等技术验证接口。
 *
 * <p>该接口只用于 Phase 01 验证统一幂等组件，不代表最终业务 API。它在任何业务副作用发生前尝试写入
 * 幂等占位：首次请求返回 201，重复请求返回 409，Redis 不可用且 fail-closed 时返回 503。</p>
 */
@RestController
@RequestMapping("/integration")
@ConditionalOnProperty(prefix = "mom.technical-probe", name = "enabled", havingValue = "true")
public class IntegrationIdempotencyProbeController {

    private static final String IDEMPOTENCY_SCOPE = "integration-probe";

    private final IdempotencyGuard idempotencyGuard;
    private final RedisIdempotencyProperties properties;

    /**
     * 创建幂等验证控制器。
     *
     * @param idempotencyGuard Redis 幂等保护接口
     * @param properties 默认 TTL 与失败策略配置
     */
    public IntegrationIdempotencyProbeController(
            IdempotencyGuard idempotencyGuard,
            RedisIdempotencyProperties properties) {
        this.idempotencyGuard = idempotencyGuard;
        this.properties = properties;
    }

    /**
     * 尝试为当前请求取得幂等执行资格。
     *
     * @param idempotencyKey 调用方提供的幂等标识；相同业务意图重试时必须保持一致
     * @return 201 表示首次取得，409 表示重复，202 表示 fail-open 绕过，503 表示 fail-closed
     */
    @PostMapping("/idempotency-probe")
    public ResponseEntity<IdempotencyProbeResponse> acquire(
            @RequestHeader(IdempotencyHeaders.IDEMPOTENCY_KEY) String idempotencyKey) {
        String correlationId = currentCorrelationId();
        Duration ttl = properties.getDefaultTtl();
        try {
            IdempotencyAcquireResult result = idempotencyGuard.tryAcquire(
                    IDEMPOTENCY_SCOPE,
                    idempotencyKey,
                    correlationId,
                    ttl);
            return ResponseEntity.status(resolveStatus(result.status()))
                    .body(new IdempotencyProbeResponse(
                            "mom-integration-server",
                            result.status().name(),
                            result.mayProceed(),
                            correlationId,
                            result.ttl().toSeconds(),
                            result.failureReason()));
        } catch (IdempotencyUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new IdempotencyProbeResponse(
                            "mom-integration-server",
                            "UNAVAILABLE",
                            false,
                            correlationId,
                            ttl.toSeconds(),
                            exception.getClass().getSimpleName()));
        }
    }

    /**
     * 将幂等结果映射为明确的 HTTP 语义。
     */
    private static HttpStatus resolveStatus(IdempotencyAcquireStatus status) {
        return switch (status) {
            case ACQUIRED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case BYPASSED -> HttpStatus.ACCEPTED;
        };
    }

    /**
     * 正常请求由 Servlet Filter 提供关联标识；极端情况下生成临时值，保证 Redis value 不为空。
     */
    private static String currentCorrelationId() {
        String correlationId = CorrelationContext.currentId();
        return correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
    }
}
