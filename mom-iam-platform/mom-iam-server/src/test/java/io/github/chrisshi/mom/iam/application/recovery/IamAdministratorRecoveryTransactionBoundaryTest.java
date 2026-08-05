package io.github.chrisshi.mom.iam.application.recovery;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/** 内置管理员恢复必须由公开 Application 方法持有本地事务的架构门禁。 */
class IamAdministratorRecoveryTransactionBoundaryTest {

    @Test
    void recoverMustBeTheOnlyPublishedTransaction() throws NoSuchMethodException {
        var method = IamAdministratorRecoveryApplicationService.class
                .getDeclaredMethod("recover", String.class);
        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(IamAdministratorRecoveryApplicationService.class.getDeclaredMethods())
                .filteredOn(candidate -> candidate.isAnnotationPresent(Transactional.class))
                .extracting(candidate -> candidate.getName())
                .containsExactly("recover");
    }
}
