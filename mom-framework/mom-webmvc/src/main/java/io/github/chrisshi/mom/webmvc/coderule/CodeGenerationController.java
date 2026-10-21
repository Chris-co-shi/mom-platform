package io.github.chrisshi.mom.webmvc.coderule;

import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.application.CodeGenerationApplication;
import io.github.chrisshi.mom.coderule.application.CodeGenerationResult;
import io.github.chrisshi.mom.coderule.field.CodeFieldMetadata;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.webmvc.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 当前 Owner 正式发号 HTTP 边界，使用独立于规则维护的权限。
 */
@RestController
@RequestMapping("/code-rules")
@RequiredArgsConstructor
public class CodeGenerationController {
    private final CodeGenerationApplication generation;
    private final CodeFieldRegistry fields;

    /**
     * 返回当前 Owner 可用于正式发号的字段及所需上下文键。
     */
    @GetMapping("/generation-fields")
    @PreAuthorize("@codeRuleManagementAccess.canGenerate(authentication)")
    public Result<List<CodeFieldMetadata>> fields() {
        return Result.success(fields.catalog());
    }

    /**
     * 以稳定规则编码及请求键申请正式编码；重复请求返回首次提交结果。
     */
    @PostMapping("/generate")
    @PreAuthorize("@codeRuleManagementAccess.canGenerate(authentication)")
    public Result<CodeGenerationResult> generate(@RequestBody GenerateRequest request, Authentication authentication) {
        if (request == null || request.requestKey() == null || !request.requestKey().matches("[A-Za-z0-9._:-]{1,128}"))
            throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, "request_key_invalid");
        Map<String, String> context = null;
        if (request.context() != null) {
            if (request.context().entrySet().stream().anyMatch(entry -> !(entry.getValue() instanceof String)))
                throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, "context_invalid");
            context = request.context().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, entry -> (String) entry.getValue()));
        }
        return Result.success(generation.generate(request.ruleCode(), context, scopedKey(authentication.getName(), request.requestKey())));
    }

    private static String scopedKey(String actor, String key) {
        try {
            byte[] input = (actor + "\u0000" + key).getBytes(StandardCharsets.UTF_8);
            return "http:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * context 只包含已注册业务字段 Provider 所需的标识。
     */
    public record GenerateRequest(String ruleCode, Map<String, Object> context, String requestKey) {
    }
}
