package io.github.chrisshi.mom.system.infrastructure.http.iam;

import feign.FeignException;
import feign.RetryableException;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceResult;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesRequest;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesResponse;
import io.github.chrisshi.mom.iam.client.IamPermissionReferenceClient;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import io.github.chrisshi.mom.system.application.catalog.port.PermissionReferenceValidationPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * IAM Permission Reference Feign 出站 Adapter。
 *
 * <p>调用强制使用 {@link Propagation#NEVER}，如果上游错误地在活动数据库事务中调用将立即失败。Adapter 不透传
 * 用户 Token，不提供 fallback，不把 IAM 错误伪造为全部有效。</p>
 */
@Component
public class IamPermissionReferenceValidationAdapter implements PermissionReferenceValidationPort {
    private final IamPermissionReferenceClient client;

    public IamPermissionReferenceValidationAdapter(IamPermissionReferenceClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ValidationResult validate(Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return new ValidationResult(java.time.Instant.EPOCH, Map.of());
        }
        ValidatePermissionReferencesResponse response;
        try {
            response = client.validate(new ValidatePermissionReferencesRequest(
                    permissionCodes.stream().sorted().toList()));
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
                    || !permissionCodes.contains(result.permissionCode())
                    || statuses.putIfAbsent(result.permissionCode(), Status.valueOf(result.status().name())) != null) {
                throw new SystemCatalogException.DependencyProtocol("IAM Permission 校验响应包含非法或重复结果");
            }
        }
        if (!statuses.keySet().equals(permissionCodes)) {
            throw new SystemCatalogException.DependencyProtocol("IAM Permission 校验响应缺少请求 Code");
        }
        return new ValidationResult(response.checkedAt(), statuses);
    }
}
