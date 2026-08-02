package io.github.chrisshi.mom.iam.application.admin;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM 管理批量 ID 上限测试。 */
class IamAdminCommandValidatorTest {
    @Test
    void normalizedIdsMustRejectUnboundedBatch() {
        assertThat(IamAdminCommandValidator.normalizedIds(ids(200), "roleIds"))
                .hasSize(200);
        assertThatThrownBy(() ->
                IamAdminCommandValidator.normalizedIds(ids(201), "roleIds"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多允许 200 项");
    }

    private static Set<String> ids(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(Integer::toString)
                .collect(Collectors.toSet());
    }
}
