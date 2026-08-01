package io.github.chrisshi.mom.resilience;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 固定 Resilience 命名 Profile 与 Feign 实例名，不冻结任何运行参数值。
 */
class MomResilienceNamesTest {

    @Test
    void shouldUseStableLowCardinalityFeignInstanceName() {
        assertThat(MomResilienceNames.feignInstance(ExampleClient.class, "validate"))
                .isEqualTo("ExampleClient_validate");
        assertThat(MomResilienceNames.DEFAULT_PROFILE).isEqualTo("default");
        assertThat(MomResilienceNames.SYSTEM_QUERY_PROFILE).isEqualTo("system-query");
    }

    @Test
    void shouldRejectBlankOrUnsafeMethodName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MomResilienceNames.feignInstance(ExampleClient.class, " "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MomResilienceNames.feignInstance(ExampleClient.class, "find/all"));
    }

    private interface ExampleClient {
    }
}
