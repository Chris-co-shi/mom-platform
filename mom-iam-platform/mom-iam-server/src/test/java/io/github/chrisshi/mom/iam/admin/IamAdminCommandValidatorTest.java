package io.github.chrisshi.mom.iam.admin;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IAM 管理批量 ID 上限测试。
 *
 * <p>该测试固定关系替换用例的最大集合，防止移除物理外键后出现无界逐项写入。校验失败无数据库副作用，
 * 不涉及并发或外部基础设施。</p>
 */
class IamAdminCommandValidatorTest {

    /** 200 项以内通过并去重，超过上限在进入事务写入前失败。 */
    @Test
    void normalizedIdsMustRejectUnboundedBatch() {
        Set<String> allowed = ids(200);
        assertThat(IamAdminCommandValidator.normalizedIds(allowed, "roleIds")).hasSize(200);
        assertThatThrownBy(() -> IamAdminCommandValidator.normalizedIds(ids(201), "roleIds"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多允许 200 项");
    }

    private static Set<String> ids(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(Integer::toString).collect(Collectors.toSet());
    }
}
