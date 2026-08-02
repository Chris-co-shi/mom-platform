package io.github.chrisshi.mom.iam.client;

import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesRequest;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * IAM Permission Code 权威批量校验 Feign Client。
 *
 * <p>调用方必须使用服务身份和有限超时。本 Client 不提供 fallback，不重试写请求，也不透传用户 Bearer Token。
 * 直连 URL 由 {@code spring.cloud.openfeign.client.config.iamPermissionReferenceClient.url} 配置；未配置时才使用
 * {@code name} 通过服务发现解析，避免注解注册阶段的嵌套占位符丢失。</p>
 */
@FeignClient(
        name = "${mom.iam.permission-reference.service-name:mom-iam-server}",
        contextId = "iamPermissionReferenceClient",
        path = "/api/iam/internal/permission-references")
public interface IamPermissionReferenceClient {

    /** 批量读取 IAM 当前 Permission Code 状态。 */
    @PostMapping("/validate")
    ValidatePermissionReferencesResponse validate(
            @RequestBody ValidatePermissionReferencesRequest request);
}
