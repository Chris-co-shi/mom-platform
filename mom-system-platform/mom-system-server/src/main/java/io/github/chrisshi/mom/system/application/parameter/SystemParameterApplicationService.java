package io.github.chrisshi.mom.system.application.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.parameter.ParameterValueNormalizer;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameter;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRepository;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.CreateCommand;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageQuery;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageView;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.ParameterView;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.UpdateCommand;
import static io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort.ChangeKind;

/**
 * System Parameter 的事务用例服务。
 *
 * <p>所有写操作位于单 System PostgreSQL 本地事务中。同 Key 写入先获取数据库事务级 Key 锁，再执行
 * 跨 Scope 类型一致性检查；业务行与 Runtime 变更 Outbox 在同一事务提交。Runtime 解析不开启数据库事务：
 * 先读取轻量权威 Header，再访问版本化 Redis Projection；Cache Miss 后按 ID 回源 PostgreSQL。</p>
 */
@Service
public class SystemParameterApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RUNTIME_READ_ATTEMPTS = 2;

    private final SystemParameterRepository repository;
    private final ParameterValueNormalizer valueNormalizer;
    private final SystemRuntimeCachePort cache;
    private final SystemRuntimeChangeEventPort events;

    public SystemParameterApplicationService(
            SystemParameterRepository repository,
            ParameterValueNormalizer valueNormalizer,
            SystemRuntimeCachePort cache,
            SystemRuntimeChangeEventPort events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.valueNormalizer = Objects.requireNonNull(valueNormalizer, "valueNormalizer");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** 创建 GLOBAL 或 APPLICATION 参数。 */
    @Transactional
    public ParameterView create(CreateCommand command) {
        Objects.requireNonNull(command, "command");
        String key = SystemParameterRules.normalizeKey(command.parameterKey());
        String scopeCode = SystemParameterRules.normalizeScopeCode(command.scopeType(), command.scopeCode());
        String value = valueNormalizer.normalize(command.valueType(), command.parameterValue());
        repository.lockParameterKey(key);
        requireCompatibleValueType(key, command.valueType(), null);
        SystemParameter parameter = SystemParameter.newParameter(
                command.scopeType(), scopeCode, key, command.valueType(), value,
                command.enabled() == null || command.enabled(),
                SystemParameterRules.normalizeDescription(command.description()));
        SystemParameter persisted = repository.insert(parameter);
        appendChange(persisted, ChangeKind.CREATED);
        return ParameterView.from(persisted);
    }

    /** 使用客户端 Version 更新参数值、类型和描述。 */
    @Transactional
    public ParameterView update(String id, UpdateCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        SystemParameter current = requireParameter(id);
        repository.lockParameterKey(current.parameterKey());
        requireCompatibleValueType(current.parameterKey(), command.valueType(), current.id());
        String value = valueNormalizer.normalize(command.valueType(), command.parameterValue());
        SystemParameter changed = current.update(command.version(), command.valueType(), value,
                SystemParameterRules.normalizeDescription(command.description()));
        if (!repository.update(changed)) {
            throw new SystemParameterException.StaleVersion("参数已被其他请求修改");
        }
        SystemParameter persisted = requireParameter(current.id());
        appendChange(persisted, ChangeKind.UPDATED);
        return ParameterView.from(persisted);
    }

    /** 使用客户端 Version 启用或禁用参数；禁用不改变类型一致性约束。 */
    @Transactional
    public ParameterView changeStatus(String id, StatusCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        SystemParameter current = requireParameter(id);
        repository.lockParameterKey(current.parameterKey());
        if (!repository.updateStatus(current.changeStatus(command.version(), command.enabled()))) {
            throw new SystemParameterException.StaleVersion("参数已被其他请求修改");
        }
        SystemParameter persisted = requireParameter(current.id());
        appendChange(persisted, ChangeKind.STATUS_CHANGED);
        return ParameterView.from(persisted);
    }

    /** 按 ID 查询管理视图，无副作用。 */
    @Transactional(readOnly = true)
    public ParameterView get(String id) {
        return ParameterView.from(requireParameter(id));
    }

    /** 按有限精确条件分页查询，固定按 Key、Scope、Code、ID 排序。 */
    @Transactional(readOnly = true)
    public PageView page(PageQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.page() < 0) {
            throw new IllegalArgumentException("page 不能小于 0");
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size 必须在 1～" + MAX_PAGE_SIZE + " 之间");
        }
        String key = query.parameterKey() == null || query.parameterKey().isBlank()
                ? null : SystemParameterRules.normalizeKey(query.parameterKey());
        String scopeCode = normalizeQueryScopeCode(query.scopeType(), query.scopeCode());
        SystemParameterRepository.ParameterPage result = repository.findPage(
                new SystemParameterRepository.ParameterQuery(
                        query.scopeType(), scopeCode, key, query.enabled(), query.page(), query.size()));
        List<ParameterView> items = result.items().stream().map(ParameterView::from).toList();
        return new PageView(items, result.total(), result.page(), result.size());
    }

    /**
     * 解析应用有效参数；enabled APPLICATION 优先，禁用时回退 enabled GLOBAL。
     *
     * <p>该方法故意不使用 {@code @Transactional}，保证 Redis 访问不发生在活动数据库事务中。每次读取先以
     * PostgreSQL Header 确认生效行和版本，数据库不可用时不会仅凭旧 Cache 返回值。</p>
     */
    public ResolvedSystemParameter resolve(String parameterKey, String applicationCode) {
        String key = SystemParameterRules.normalizeKey(parameterKey);
        String application = applicationCode == null || applicationCode.isBlank()
                ? null
                : SystemParameterRules.normalizeScopeCode(ParameterScopeType.APPLICATION, applicationCode);
        String lookupScope = application == null ? SystemParameterRules.GLOBAL_SCOPE_CODE : application;

        for (int attempt = 0; attempt < MAX_RUNTIME_READ_ATTEMPTS; attempt++) {
            Optional<SystemParameterRepository.RuntimeHeader> selected =
                    selectEffectiveHeader(key, application);
            if (selected.isEmpty()) {
                throw new SystemParameterException.NotFound("有效参数不存在");
            }
            var header = selected.orElseThrow();
            Optional<ResolvedSystemParameter> cached = cache.findParameter(
                    lookupScope, key, header.scopeType(), header.scopeCode(), header.version());
            if (cached.filter(value -> matches(value, header)).isPresent()) {
                return cached.orElseThrow();
            }
            Optional<SystemParameter> current = repository.findById(header.id());
            if (current.filter(value -> matches(value, header)).isPresent()) {
                ResolvedSystemParameter resolved = resolved(current.orElseThrow());
                cache.putParameter(
                        lookupScope, key, header.scopeType(), header.scopeCode(), header.version(), resolved);
                return resolved;
            }
        }
        throw new IllegalStateException("参数在 Runtime 解析过程中持续发生并发变化");
    }

    private Optional<SystemParameterRepository.RuntimeHeader> selectEffectiveHeader(
            String key, String applicationCode) {
        if (applicationCode != null) {
            var override = repository.findRuntimeHeader(
                    ParameterScopeType.APPLICATION, applicationCode, key);
            if (override.isPresent() && override.orElseThrow().enabled()) {
                return override;
            }
        }
        var global = repository.findRuntimeHeader(
                ParameterScopeType.GLOBAL, SystemParameterRules.GLOBAL_SCOPE_CODE, key);
        return global.filter(SystemParameterRepository.RuntimeHeader::enabled);
    }

    private void appendChange(SystemParameter parameter, ChangeKind changeKind) {
        events.parameterChanged(new SystemRuntimeChangeEventPort.ParameterChangedEvent(
                parameter.id(),
                parameter.parameterKey(),
                parameter.scopeType(),
                parameter.scopeCode(),
                parameter.version(),
                parameter.enabled(),
                changeKind));
    }

    private void requireCompatibleValueType(String key, ParameterValueType requested, String excludedId) {
        if (requested == null) {
            throw new IllegalArgumentException("valueType 不能为空");
        }
        boolean incompatible = repository.findAllByKey(key).stream()
                .filter(parameter -> !Objects.equals(parameter.id(), excludedId))
                .anyMatch(parameter -> parameter.valueType() != requested);
        if (incompatible) {
            throw new SystemParameterException.Conflict(
                    "同一 parameterKey 的 GLOBAL 与 APPLICATION valueType 必须一致");
        }
    }

    private SystemParameter requireParameter(String id) {
        String normalizedId = requireId(id);
        return repository.findById(normalizedId)
                .orElseThrow(() -> new SystemParameterException.NotFound("参数不存在"));
    }

    private static boolean matches(
            ResolvedSystemParameter value,
            SystemParameterRepository.RuntimeHeader header) {
        return value.parameterKey().equals(header.parameterKey())
                && value.valueType() == header.valueType()
                && value.resolvedScopeType() == header.scopeType()
                && value.resolvedScopeCode().equals(header.scopeCode())
                && value.version() == header.version()
                && value.updatedAt().equals(header.updatedAt());
    }

    private static boolean matches(
            SystemParameter value,
            SystemParameterRepository.RuntimeHeader header) {
        return value.id().equals(header.id())
                && value.scopeType() == header.scopeType()
                && value.scopeCode().equals(header.scopeCode())
                && value.parameterKey().equals(header.parameterKey())
                && value.valueType() == header.valueType()
                && value.enabled() == header.enabled()
                && value.version() == header.version()
                && value.updatedAt().equals(header.updatedAt());
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank() || id.length() > 19) {
            throw new IllegalArgumentException("id 必须是 1～19 位字符串");
        }
        return id.trim();
    }

    private static void requireVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version 不能小于 0");
        }
    }

    private static String normalizeQueryScopeCode(ParameterScopeType scopeType, String scopeCode) {
        if (scopeType == null) {
            if (scopeCode == null || scopeCode.isBlank()) {
                return null;
            }
            return SystemParameterRules.normalizeScopeCode(ParameterScopeType.APPLICATION, scopeCode);
        }
        return SystemParameterRules.normalizeScopeCode(scopeType, scopeCode);
    }

    private static ResolvedSystemParameter resolved(SystemParameter parameter) {
        return new ResolvedSystemParameter(parameter.parameterKey(), parameter.valueType(),
                parameter.parameterValue(), parameter.scopeType(), parameter.scopeCode(),
                parameter.version(), parameter.updatedAt());
    }
}
