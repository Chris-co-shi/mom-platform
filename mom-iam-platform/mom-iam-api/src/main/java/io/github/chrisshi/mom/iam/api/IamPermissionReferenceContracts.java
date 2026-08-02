package io.github.chrisshi.mom.iam.api;

import java.time.Instant;
import java.util.List;

/**
 * IAM Permission Code 权威批量校验的稳定跨服务契约。
 *
 * <p>契约只暴露 Permission Code 与当前权威状态，不暴露数据库 ID、Role、Assignment、用户、审计字段或
 * OAuth Client 信息。调用方不得把返回结果复制为长期授权权威；业务 API 仍必须独立验证当前 JWT。</p>
 */
public final class IamPermissionReferenceContracts {
    private IamPermissionReferenceContracts() {
    }

    /** Permission Code 在 IAM 权威目录中的当前状态。 */
    public enum PermissionReferenceStatus {
        ENABLED,
        DISABLED,
        UNKNOWN
    }

    /** 最多一千个 Permission Code 的批量校验请求。 */
    public record ValidatePermissionReferencesRequest(List<String> permissionCodes) {
        public ValidatePermissionReferencesRequest {
            permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
        }
    }

    /** 单个 Permission Code 的权威校验结果。 */
    public record PermissionReferenceResult(
            String permissionCode,
            PermissionReferenceStatus status) {
    }

    /** 一次权威批量校验响应。 */
    public record ValidatePermissionReferencesResponse(
            Instant checkedAt,
            List<PermissionReferenceResult> results) {
        public ValidatePermissionReferencesResponse {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}
