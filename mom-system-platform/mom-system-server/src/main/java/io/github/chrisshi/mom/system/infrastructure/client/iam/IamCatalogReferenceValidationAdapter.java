package io.github.chrisshi.mom.system.infrastructure.client.iam;

import feign.FeignException;
import feign.RetryableException;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceResult;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesRequest;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesResponse;
import io.github.chrisshi.mom.iam.client.IamPermissionReferenceClient;
import io.github.chrisshi.mom.resilience.ResilienceTransactionGuard;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import io.github.chrisshi.mom.system.application.catalog.port.CatalogReferenceValidationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * IAM Catalog Reference Feign 出站 Client Adapter。
 *
 * <p>调用强制使用 {@link Propagation#NEVER}，如果上游错误地在活动数据库事务中调用将立即失败。Adapter 不透传
 * 用户 Token，不提供 fallback，不把 IAM 错误伪造为全部有效，也不在 System 内定义 Permission 权威对象。</p>
 */
@Component
public class IamCatalogReferenceValidationAdapter implements CatalogReferenceValidationPort {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(IamCatalogReferenceValidationAdapter.class);
    private final IamPermissionReferenceClient client;

    public IamCatalogReferenceValidationAdapter(IamPermissionReferenceClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ValidationResult validate(Set<String> referenceCodes) {
        if (referenceCodes == null || referenceCodes.isEmpty()) {
            return new ValidationResult(java.time.Instant.EPOCH, Map.of());
        }
        LOGGER.info("IAM Catalog Reference 批量校验开始。count={}", referenceCodes.size());
        ResilienceTransactionGuard.requireNoActiveTransaction("System IAM Permission Reference Query");
        ValidatePermissionReferencesResponse response;
        try {
            response = client.validate(new ValidatePermissionReferencesRequest(
                    referenceCodes.stream().sorted().toList()));
        } catch (NoFallbackAvailableException exception) {
            throw mapCircuitBreakerFailure(exception);
        } catch (RetryableException exception) {
            throw new SystemCatalogException.DependencyUnavailable(
                    "IAM Permission 权威服务暂时不可用", exception);
        } catch (FeignException exception) {
            if (exception.status() < 0 || exception.status() >= 500) {
                throw new SystemCatalogException.DependencyUnavailable(
                        "IAM Permission 权威服务暂时不可用", exception);
            }
            throw new SystemCatalogException.DependencyProtocol(
                    "IAM Permission 权威服务拒绝或返回非法协议状态", exception);
        }
        if (response == null || response.checkedAt() == null || response.results() == null) {
            throw new SystemCatalogException.DependencyProtocol("IAM Permission 校验响应不完整");
        }
        Map<String, Status> statuses = new LinkedHashMap<>();
        for (PermissionReferenceResult result : response.results()) {
            if (result == null || result.permissionCode() == null || result.status() == null
                    || !referenceCodes.contains(result.permissionCode())
                    || statuses.putIfAbsent(result.permissionCode(), Status.valueOf(result.status().name())) != null) {
                throw new SystemCatalogException.DependencyProtocol("IAM Permission 校验响应包含非法或重复结果");
            }
        }
        if (!statuses.keySet().equals(referenceCodes)) {
            throw new SystemCatalogException.DependencyProtocol("IAM Permission 校验响应缺少请求 Code");
        }
        LOGGER.info("IAM Catalog Reference 批量校验完成。count={}", referenceCodes.size());
        return new ValidationResult(response.checkedAt(), statuses);
    }

    /**
     * 把 Spring Cloud CircuitBreaker 的无 Fallback 结果映射为既有 System 依赖错误模型。
     *
     * <p>Open Circuit、timeout、bulkhead 拒绝和连接失败统一视为依赖不可用；被 CircuitBreaker 包装的 HTTP 4xx
     * 仍保留协议错误。任何分支都不会返回“全部 Permission 有效”等伪造成功。</p>
     */
    private static SystemCatalogException mapCircuitBreakerFailure(NoFallbackAvailableException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof FeignException feignException
                && feignException.status() >= 400
                && feignException.status() < 500) {
            return new SystemCatalogException.DependencyProtocol(
                    "IAM Permission 权威服务拒绝或返回非法协议状态", exception);
        }
        return new SystemCatalogException.DependencyUnavailable(
                "IAM Permission 权威服务暂时不可用", exception);
    }
}
