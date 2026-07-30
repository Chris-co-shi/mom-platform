package io.github.chrisshi.mom.system.application.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.domain.parameter.ParameterValueNormalizer;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameter;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRepository;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.CreateCommand;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageQuery;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageView;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.ParameterView;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.UpdateCommand;

/**
 * System Parameter 的事务用例服务。
 *
 * <p>所有写操作位于单 System PostgreSQL 本地事务中。同 Key 写入先获取数据库事务级 Key 锁，再执行
 * 跨 Scope 类型一致性检查；唯一约束仍兜底同 Scope 并发。服务只依赖领域 Port，不依赖 Mapper、Entity、
 * HTTP、Redis、MQ 或 Seata。基础设施不可用时失败向上传播，不缓存或伪造默认参数。</p>
 */
@Service
public class SystemParameterApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private final SystemParameterRepository repository;
    private final ParameterValueNormalizer valueNormalizer;

    public SystemParameterApplicationService(
            SystemParameterRepository repository,
            ParameterValueNormalizer valueNormalizer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.valueNormalizer = Objects.requireNonNull(valueNormalizer, "valueNormalizer");
    }

    /**
     * 创建 GLOBAL 或 APPLICATION 参数。
     *
     * @param command 不含客户端审计字段的创建命令
     * @return 数据库填充 ID、审计和版本后的视图
     * @throws IllegalArgumentException 输入非法或疑似 Secret Key
     * @throws SystemParameterException.Conflict 唯一性或跨 Scope 类型冲突
     */
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
        return ParameterView.from(repository.insert(parameter));
    }

    /**
     * 使用客户端 Version 更新参数值、类型和描述。
     *
     * @param id String 技术主键
     * @param command 版本化更新命令
     * @return 更新后的完整视图
     * @throws SystemParameterException.NotFound 参数不存在
     * @throws SystemParameterException.StaleVersion 版本冲突
     */
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
        return ParameterView.from(requireParameter(current.id()));
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
        return ParameterView.from(requireParameter(current.id()));
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
     * @param parameterKey 参数键
     * @param applicationCode 可选独立应用编码，不是 IAM clientId
     * @return 类型与规范字符串组成的稳定跨服务契约
     * @throws SystemParameterException.NotFound 无有效值
     */
    @Transactional(readOnly = true)
    public ResolvedSystemParameter resolve(String parameterKey, String applicationCode) {
        String key = SystemParameterRules.normalizeKey(parameterKey);
        if (applicationCode != null && !applicationCode.isBlank()) {
            String app = SystemParameterRules.normalizeScopeCode(ParameterScopeType.APPLICATION, applicationCode);
            var override = repository.findByScopeAndKey(ParameterScopeType.APPLICATION, app, key);
            if (override.isPresent() && override.orElseThrow().enabled()) {
                return resolved(override.orElseThrow());
            }
        }
        var global = repository.findByScopeAndKey(
                ParameterScopeType.GLOBAL, SystemParameterRules.GLOBAL_SCOPE_CODE, key);
        if (global.isPresent() && global.orElseThrow().enabled()) {
            return resolved(global.orElseThrow());
        }
        throw new SystemParameterException.NotFound("有效参数不存在");
    }

    private void requireCompatibleValueType(String key, ParameterValueType requested, String excludedId) {
        if (requested == null) {
            throw new IllegalArgumentException("valueType 不能为空");
        }
        boolean incompatible = repository.findAllByKey(key).stream()
                .filter(parameter -> !Objects.equals(parameter.id(), excludedId))
                .anyMatch(parameter -> parameter.valueType() != requested);
        if (incompatible) {
            throw new SystemParameterException.Conflict("同一 parameterKey 的 GLOBAL 与 APPLICATION valueType 必须一致");
        }
    }

    private SystemParameter requireParameter(String id) {
        String normalizedId = requireId(id);
        return repository.findById(normalizedId)
                .orElseThrow(() -> new SystemParameterException.NotFound("参数不存在"));
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
