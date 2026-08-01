package io.github.chrisshi.mom.system.infrastructure.client.iam;

import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceResult;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceStatus;
import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.ValidatePermissionReferencesResponse;
import io.github.chrisshi.mom.iam.client.IamPermissionReferenceClient;
import io.github.chrisshi.mom.system.application.catalog.port.CatalogReferenceValidationPort;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/** Feign Catalog Reference Adapter 的真实 Spring Proxy 事务外门禁测试。 */
class IamCatalogReferenceValidationAdapterTest {
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void mustWorkOutsideTransactionAndRejectActiveTransactionBeforeFeignCall() {
        IamPermissionReferenceClient client = context.getBean(IamPermissionReferenceClient.class);
        when(client.validate(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ValidatePermissionReferencesResponse(
                    Instant.EPOCH,
                    List.of(new PermissionReferenceResult(
                            "iam:user:read", PermissionReferenceStatus.ENABLED)));
        });
        var adapter = context.getBean(IamCatalogReferenceValidationAdapter.class);
        assertThat(adapter.validate(Set.of("iam:user:read")).statuses())
                .containsEntry("iam:user:read", CatalogReferenceValidationPort.Status.ENABLED);

        TransactionTemplate transaction = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                adapter.validate(Set.of("iam:user:read"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        verify(client, times(1)).validate(any());
    }

    @Test
    void openCircuitWithoutFallbackMustMapToDependencyUnavailable() {
        IamPermissionReferenceClient client = context.getBean(IamPermissionReferenceClient.class);
        when(client.validate(any())).thenThrow(new NoFallbackAvailableException(
                "circuit open", new IllegalStateException("open")));

        assertThatThrownBy(() -> context.getBean(IamCatalogReferenceValidationAdapter.class)
                .validate(Set.of("iam:user:read")))
                .isInstanceOf(SystemCatalogException.DependencyUnavailable.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        IamPermissionReferenceClient client() {
            return mock(IamPermissionReferenceClient.class);
        }

        @Bean
        IamCatalogReferenceValidationAdapter adapter(IamPermissionReferenceClient client) {
            return new IamCatalogReferenceValidationAdapter(client);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new ThreadBoundTestTransactionManager();
        }
    }

    /** 仅用于验证 Spring Propagation 的轻量事务管理器。 */
    static final class ThreadBoundTestTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active.set(true);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            active.remove();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            active.remove();
        }
    }
}
