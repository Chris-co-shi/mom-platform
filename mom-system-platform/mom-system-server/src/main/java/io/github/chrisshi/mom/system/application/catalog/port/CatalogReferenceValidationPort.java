package io.github.chrisshi.mom.system.application.catalog.port;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Catalog 稳定 Reference 权威批量校验出站 Port。
 *
 * <p>Application 只依赖稳定 Code 状态，不定义 IAM Permission 权威对象，也不依赖 Feign、OAuth2、IAM API DTO
 * 或网络异常。调用必须发生在 System PostgreSQL 事务之外。</p>
 */
public interface CatalogReferenceValidationPort {

    /** 批量读取所有输入 Code 的权威状态。 */
    ValidationResult validate(Set<String> referenceCodes);

    enum Status {
        ENABLED,
        DISABLED,
        UNKNOWN
    }

    record ValidationResult(Instant checkedAt, Map<String, Status> statuses) {
        public ValidationResult {
            statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
        }
    }
}
