package io.github.chrisshi.mom.system.application.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.parameter.ParameterValueNormalizer;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameter;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.CreateCommand;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageQuery;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.UpdateCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** System Parameter 事务编排、类型一致性、解析、Cache 与事件语义单元测试。 */
class SystemParameterApplicationServiceTest {
    private InMemoryRepository repository;
    private SystemRuntimeCachePort cache;
    private SystemRuntimeChangeEventPort events;
    private SystemParameterApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        cache = mock(SystemRuntimeCachePort.class);
        events = mock(SystemRuntimeChangeEventPort.class);
        service = new SystemParameterApplicationService(
                repository,
                new ParameterValueNormalizer(JsonMapper.builder().build()),
                cache,
                events);
    }

    @Test
    void shouldCreateGlobalWithCanonicalValueAndAppendNonSensitiveEvent() {
        var created = service.create(command(ParameterScopeType.GLOBAL, null,
                "Feature.Timeout", ParameterValueType.INTEGER, "00012"));
        assertThat(created.scopeCode()).isEmpty();
        assertThat(created.parameterKey()).isEqualTo("feature.timeout");
        assertThat(created.parameterValue()).isEqualTo("12");
        assertThat(created.createdBy()).isEqualTo("test-actor");

        verify(events).parameterChanged(any(SystemRuntimeChangeEventPort.ParameterChangedEvent.class));
        assertThat(SystemRuntimeChangeEventPort.ParameterChangedEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("parameterValue", "value", "secret");
    }

    @Test
    void shouldCreateApplicationWithNormalizedCode() {
        var created = service.create(command(ParameterScopeType.APPLICATION, "MOM-WEB",
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        assertThat(created.scopeCode()).isEqualTo("mom-web");
    }

    @Test
    void duplicateScopeAndKeyMustConflict() {
        service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        assertThatThrownBy(() -> service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "13")))
                .isInstanceOf(SystemParameterException.Conflict.class);
    }

    @Test
    void overrideMustKeepGlobalTypeEvenWhenDisabled() {
        service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        var override = service.create(command(ParameterScopeType.APPLICATION, "mom-web",
                "feature.timeout", ParameterValueType.INTEGER, "20"));
        service.changeStatus(override.id(), new StatusCommand(false, override.version()));

        assertThatThrownBy(() -> service.update(override.id(),
                new UpdateCommand(1L, ParameterValueType.STRING, "twenty", null)))
                .isInstanceOf(SystemParameterException.Conflict.class);
    }

    @Test
    void creatingGlobalMustMatchAllExistingOverrides() {
        service.create(command(ParameterScopeType.APPLICATION, "mom-web",
                "feature.timeout", ParameterValueType.INTEGER, "20"));
        assertThatThrownBy(() -> service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.STRING, "20")))
                .isInstanceOf(SystemParameterException.Conflict.class);
    }

    @Test
    void enabledApplicationMustWinDuringResolution() {
        service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        service.create(command(ParameterScopeType.APPLICATION, "mom-web",
                "feature.timeout", ParameterValueType.INTEGER, "20"));
        var resolved = service.resolve("feature.timeout", "mom-web");
        assertThat(resolved.parameterValue()).isEqualTo("20");
        assertThat(resolved.resolvedScopeType()).isEqualTo(ParameterScopeType.APPLICATION);
    }

    @Test
    void disabledApplicationMustFallBackToGlobal() {
        service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        var override = service.create(command(ParameterScopeType.APPLICATION, "mom-web",
                "feature.timeout", ParameterValueType.INTEGER, "20"));
        service.changeStatus(override.id(), new StatusCommand(false, 0L));
        assertThat(service.resolve("feature.timeout", "mom-web").parameterValue()).isEqualTo("12");
    }

    @Test
    void matchingVersionedCacheMustBeUsedAfterAuthorityHeaderRead() {
        var created = service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        ResolvedSystemParameter cached = new ResolvedSystemParameter(
                created.parameterKey(),
                created.valueType(),
                "13",
                created.scopeType(),
                created.scopeCode(),
                created.version(),
                created.updatedAt());
        org.mockito.Mockito.when(cache.findParameter(
                        "",
                        created.parameterKey(),
                        created.scopeType(),
                        created.scopeCode(),
                        created.version()))
                .thenReturn(Optional.of(cached));

        assertThat(service.resolve("feature.timeout", null).parameterValue()).isEqualTo("13");
    }

    @Test
    void missingEffectiveValueMustReturnNotFound() {
        assertThatThrownBy(() -> service.resolve("feature.timeout", "mom-web"))
                .isInstanceOf(SystemParameterException.NotFound.class);
    }

    @Test
    void optimisticUpdateMustIncrementAndRejectStaleVersion() {
        var created = service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        var updated = service.update(created.id(),
                new UpdateCommand(created.version(), ParameterValueType.INTEGER, "0013", "updated"));
        assertThat(updated.parameterValue()).isEqualTo("13");
        assertThat(updated.version()).isEqualTo(1L);
        assertThatThrownBy(() -> service.update(created.id(),
                new UpdateCommand(created.version(), ParameterValueType.INTEGER, "14", null)))
                .isInstanceOf(SystemParameterException.StaleVersion.class);
    }

    @Test
    void statusChangeMustUseVersionAndPageMustBeBounded() {
        var created = service.create(command(ParameterScopeType.GLOBAL, null,
                "feature.timeout", ParameterValueType.INTEGER, "12"));
        var disabled = service.changeStatus(created.id(), new StatusCommand(false, 0L));
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.version()).isEqualTo(1L);
        assertThatThrownBy(() -> service.changeStatus(created.id(), new StatusCommand(true, 0L)))
                .isInstanceOf(SystemParameterException.StaleVersion.class);
        assertThat(service.page(new PageQuery(null, null, null, false, 0, 20)).items())
                .extracting(SystemParameterApplicationModels.ParameterView::id)
                .containsExactly(created.id());
        assertThatThrownBy(() -> service.page(new PageQuery(null, null, null, null, 0, 101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientCommandsMustNotExposeAuditFields() {
        assertThat(CreateCommand.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("createdBy", "updatedBy", "operator");
        assertThat(UpdateCommand.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("createdBy", "updatedBy", "operator");
    }

    private static CreateCommand command(
            ParameterScopeType scopeType,
            String scopeCode,
            String key,
            ParameterValueType type,
            String value) {
        return new CreateCommand(scopeType, scopeCode, key, type, value, null, true);
    }

    private static final class InMemoryRepository implements SystemParameterRepository {
        private final Map<String, SystemParameter> values = new LinkedHashMap<>();
        private long sequence;

        @Override
        public void lockParameterKey(String parameterKey) {
        }

        @Override
        public Optional<SystemParameter> findById(String id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<SystemParameter> findByScopeAndKey(
                ParameterScopeType scopeType, String scopeCode, String parameterKey) {
            return values.values().stream().filter(parameter -> parameter.scopeType() == scopeType
                    && parameter.scopeCode().equals(scopeCode)
                    && parameter.parameterKey().equals(parameterKey)).findFirst();
        }

        @Override
        public Optional<RuntimeHeader> findRuntimeHeader(
                ParameterScopeType scopeType, String scopeCode, String parameterKey) {
            return findByScopeAndKey(scopeType, scopeCode, parameterKey)
                    .map(parameter -> new RuntimeHeader(
                            parameter.id(),
                            parameter.scopeType(),
                            parameter.scopeCode(),
                            parameter.parameterKey(),
                            parameter.valueType(),
                            parameter.enabled(),
                            parameter.version(),
                            parameter.updatedAt()));
        }

        @Override
        public List<SystemParameter> findAllByKey(String parameterKey) {
            return values.values().stream()
                    .filter(parameter -> parameter.parameterKey().equals(parameterKey)).toList();
        }

        @Override
        public SystemParameter insert(SystemParameter parameter) {
            if (findByScopeAndKey(
                    parameter.scopeType(),
                    parameter.scopeCode(),
                    parameter.parameterKey()).isPresent()) {
                throw new SystemParameterException.Conflict("duplicate");
            }
            String id = String.valueOf(++sequence);
            Instant now = Instant.parse("2026-07-30T00:00:00Z");
            SystemParameter persisted = new SystemParameter(
                    id,
                    parameter.scopeType(),
                    parameter.scopeCode(),
                    parameter.parameterKey(),
                    parameter.valueType(),
                    parameter.parameterValue(),
                    parameter.enabled(),
                    0L,
                    parameter.description(),
                    "test-actor",
                    now,
                    "test-actor",
                    now);
            values.put(id, persisted);
            return persisted;
        }

        @Override
        public boolean update(SystemParameter parameter) {
            SystemParameter current = values.get(parameter.id());
            if (current == null || current.version() != parameter.version()) {
                return false;
            }
            values.put(parameter.id(), persistedUpdate(
                    current,
                    parameter.valueType(),
                    parameter.parameterValue(),
                    current.enabled(),
                    parameter.description()));
            return true;
        }

        @Override
        public boolean updateStatus(SystemParameter parameter) {
            SystemParameter current = values.get(parameter.id());
            if (current == null || current.version() != parameter.version()) {
                return false;
            }
            values.put(parameter.id(), persistedUpdate(
                    current,
                    current.valueType(),
                    current.parameterValue(),
                    parameter.enabled(),
                    current.description()));
            return true;
        }

        @Override
        public ParameterPage findPage(ParameterQuery query) {
            List<SystemParameter> filtered = new ArrayList<>(values.values().stream()
                    .filter(parameter -> query.scopeType() == null
                            || parameter.scopeType() == query.scopeType())
                    .filter(parameter -> query.scopeCode() == null
                            || parameter.scopeCode().equals(query.scopeCode()))
                    .filter(parameter -> query.parameterKey() == null
                            || parameter.parameterKey().equals(query.parameterKey()))
                    .filter(parameter -> query.enabled() == null
                            || parameter.enabled() == query.enabled())
                    .sorted(Comparator.comparing(SystemParameter::parameterKey)
                            .thenComparing(SystemParameter::id))
                    .toList());
            int from = Math.min(query.page() * query.size(), filtered.size());
            int to = Math.min(from + query.size(), filtered.size());
            return new ParameterPage(
                    filtered.subList(from, to),
                    filtered.size(),
                    query.page(),
                    query.size());
        }

        private static SystemParameter persistedUpdate(
                SystemParameter current,
                ParameterValueType type,
                String value,
                boolean enabled,
                String description) {
            return new SystemParameter(
                    current.id(),
                    current.scopeType(),
                    current.scopeCode(),
                    current.parameterKey(),
                    type,
                    value,
                    enabled,
                    current.version() + 1,
                    description,
                    current.createdBy(),
                    current.createdAt(),
                    "test-actor",
                    current.updatedAt().plusSeconds(1));
        }
    }
}
