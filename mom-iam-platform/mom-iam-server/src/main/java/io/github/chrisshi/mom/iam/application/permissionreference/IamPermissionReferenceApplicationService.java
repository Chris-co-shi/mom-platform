package io.github.chrisshi.mom.iam.application.permissionreference;

import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceResult;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceStatus;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesResponse;
import io.github.chrisshi.mom.iam.application.permissionreference.port.IamPermissionReferenceQueryPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * IAM Permission Code 权威批量校验用例。
 *
 * <p>服务只执行单次本地只读查询，不依赖 HTTP、Feign、System 或其他服务。输入去重后最多一千个，响应保持
 * 首次出现顺序；不存在的 Code 明确返回 UNKNOWN，不通过缺失记录伪造 ENABLED。</p>
 */
public final class IamPermissionReferenceApplicationService {
    public static final int MAX_CODES = 1000;
    private static final Pattern PERMISSION_CODE = Pattern.compile(
            "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    private final IamPermissionReferenceQueryPort queries;
    private final Clock clock;

    public IamPermissionReferenceApplicationService(
            IamPermissionReferenceQueryPort queries,
            Clock clock) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 批量校验 Permission Code。
     *
     * @param rawCodes 客户端提交的 Code；允许空集合但不允许 null 元素或非法格式
     * @return 权威状态与检查时间
     */
    public ValidatePermissionReferencesResponse validate(List<String> rawCodes) {
        Set<String> codes = normalize(rawCodes);
        if (codes.isEmpty()) {
            return new ValidatePermissionReferencesResponse(clock.instant(), List.of());
        }
        Map<String, IamRecordStatus> actual = queries.findStatusesByCodes(codes);
        List<PermissionReferenceResult> results = codes.stream()
                .map(code -> new PermissionReferenceResult(code, toStatus(actual.get(code))))
                .toList();
        return new ValidatePermissionReferencesResponse(clock.instant(), results);
    }

    private static Set<String> normalize(List<String> rawCodes) {
        if (rawCodes == null) {
            throw new IllegalArgumentException("permissionCodes 不能为空");
        }
        if (rawCodes.size() > MAX_CODES) {
            throw new IllegalArgumentException("permissionCodes 最多 1000 个");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawCode : rawCodes) {
            if (rawCode == null || rawCode.isBlank()) {
                throw new IllegalArgumentException("permissionCode 不能为空");
            }
            String code = rawCode.trim().toLowerCase(Locale.ROOT);
            if (code.length() > 160 || !PERMISSION_CODE.matcher(code).matches()) {
                throw new IllegalArgumentException("permissionCode 格式非法");
            }
            normalized.add(code);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static PermissionReferenceStatus toStatus(IamRecordStatus status) {
        if (status == null) {
            return PermissionReferenceStatus.UNKNOWN;
        }
        return status == IamRecordStatus.ENABLED
                ? PermissionReferenceStatus.ENABLED
                : PermissionReferenceStatus.DISABLED;
    }
}
