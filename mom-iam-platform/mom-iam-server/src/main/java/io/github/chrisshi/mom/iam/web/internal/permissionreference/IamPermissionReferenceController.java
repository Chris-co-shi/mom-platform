package io.github.chrisshi.mom.iam.web.internal.permissionreference;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesResponse;
import io.github.chrisshi.mom.iam.application.permissionreference.IamPermissionReferenceApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间 Permission Code 权威批量校验 HTTP 入站 Adapter。
 *
 * <p>端点只接受具有精确 OAuth2 Scope 的服务身份，不复用 IAM Admin 用户权限。请求严格拒绝未知字段，避免
 * 客户端静默提交身份、审计或授权字段。</p>
 */
@RestController
@ConditionalOnBean(IamPermissionReferenceApplicationService.class)
@RequestMapping("/api/iam/internal/permission-references")
@PreAuthorize("hasAuthority('SCOPE_iam.permission-reference.read')")
public class IamPermissionReferenceController {
    private final IamPermissionReferenceApplicationService service;

    public IamPermissionReferenceController(IamPermissionReferenceApplicationService service) {
        this.service = service;
    }

    /** 批量返回 IAM 当前 Permission Code 权威状态。 */
    @PostMapping("/validate")
    public ValidatePermissionReferencesResponse validate(
            @RequestBody ValidatePermissionReferencesRequest request) {
        return service.validate(request.permissionCodes());
    }

    /** 严格白名单请求；未知字段在反序列化阶段失败。 */
    public static final class ValidatePermissionReferencesRequest {
        private final List<String> permissionCodes;

        @JsonCreator
        public ValidatePermissionReferencesRequest(
                @JsonProperty("permissionCodes") List<String> permissionCodes) {
            this.permissionCodes = permissionCodes;
        }

        public List<String> permissionCodes() {
            return permissionCodes;
        }

        @JsonAnySetter
        void rejectUnknown(String fieldName, JsonNode ignored) {
            throw new IllegalArgumentException("Permission 校验请求包含未声明字段");
        }
    }
}
