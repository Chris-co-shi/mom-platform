package io.github.chrisshi.mom.i18n.autoconfigure;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.i18n.store.JdbcI18nStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class I18nJdbcAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(I18nJdbcAutoConfiguration.class));

    @Test
    void ownerMustBeExplicit() {
        runner.run(context -> assertThat(context).doesNotHaveBean(I18nStore.class));
    }

    @Test
    void configuredOwnerCreatesStoreForOnlyItsTables() {
        runner.withUserConfiguration(TechnicalBeans.class).withPropertyValues("mom.i18n.owner=mdm")
                .run(context -> {
                    assertThat(context).hasSingleBean(I18nStore.class);
                    assertThat(context.getBean(I18nStore.class)).isInstanceOf(JdbcI18nStore.class);
                });
    }

    @Test
    void customStoreTakesPrecedenceWithoutTechnicalDependencies() {
        runner.withUserConfiguration(CustomStore.class).withPropertyValues("mom.i18n.owner=auth")
                .run(context -> {
                    assertThat(context).hasSingleBean(I18nStore.class);
                    assertThat(context).doesNotHaveBean(JdbcI18nStore.class);
                });
    }

    @Test
    void unsupportedOwnerFailsFast() {
        runner.withUserConfiguration(TechnicalBeans.class).withPropertyValues("mom.i18n.owner=qms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingTechnicalDependencyFailsFast() {
        runner.withPropertyValues("mom.i18n.owner=system").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingIdCapabilityFailsFast() {
        runner.withUserConfiguration(MissingIdBeans.class).withPropertyValues("mom.i18n.owner=auth")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingTransactionManagerFailsFast() {
        runner.withUserConfiguration(MissingTransactionBeans.class).withPropertyValues("mom.i18n.owner=auth")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void mismatchedTransactionDataSourceFailsFast() {
        runner.withUserConfiguration(MismatchedTransactionBeans.class).withPropertyValues("mom.i18n.owner=auth")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class TechnicalBeans {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean IdGenerator ids() { return () -> "1000000000000000001"; }
        @Bean CurrentActorProvider actors() { return java.util.Optional::empty; }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStore {
        @Bean I18nStore store() { return mock(I18nStore.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingIdBeans {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean CurrentActorProvider actors() { return java.util.Optional::empty; }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingTransactionBeans {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean IdGenerator ids() { return () -> "1000000000000000001"; }
        @Bean CurrentActorProvider actors() { return java.util.Optional::empty; }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class MismatchedTransactionBeans {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean DataSource otherDataSource() { return mock(DataSource.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource otherDataSource) {
            return new DataSourceTransactionManager(otherDataSource);
        }
        @Bean IdGenerator ids() { return () -> "1000000000000000001"; }
        @Bean CurrentActorProvider actors() { return java.util.Optional::empty; }
        @Bean Clock clock() { return Clock.systemUTC(); }
    }
}
