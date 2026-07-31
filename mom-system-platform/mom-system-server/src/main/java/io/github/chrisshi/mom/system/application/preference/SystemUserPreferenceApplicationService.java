package io.github.chrisshi.mom.system.application.preference;

import io.github.chrisshi.mom.system.api.ResolvedUserPreference;
import io.github.chrisshi.mom.system.api.UserDensity;
import io.github.chrisshi.mom.system.api.UserThemeMode;
import io.github.chrisshi.mom.system.domain.preference.ColumnSetting;
import io.github.chrisshi.mom.system.domain.preference.Density;
import io.github.chrisshi.mom.system.domain.preference.DisplayTimezone;
import io.github.chrisshi.mom.system.domain.preference.FilterSetting;
import io.github.chrisshi.mom.system.domain.preference.PageSize;
import io.github.chrisshi.mom.system.domain.preference.PreferenceRules;
import io.github.chrisshi.mom.system.domain.preference.SortSetting;
import io.github.chrisshi.mom.system.domain.preference.StalePreferenceVersionException;
import io.github.chrisshi.mom.system.domain.preference.SupportedLocale;
import io.github.chrisshi.mom.system.domain.preference.ThemeMode;
import io.github.chrisshi.mom.system.domain.preference.UserPreference;
import io.github.chrisshi.mom.system.domain.preference.UserPreferenceRepository;
import io.github.chrisshi.mom.system.domain.preference.UserViewSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ColumnCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.FilterCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ResetCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveDisplayPreferenceCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveViewSettingCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SortCommand;

/**
 * System 用户显示偏好与受限视图设置的事务用例服务。
 *
 * <p>当前用户只由 CurrentPreferenceUserProvider 提供；所有查询都强制携带该 userId，客户端没有 IDOR
 * 参数。首次创建由数据库 Unique 兜底，更新由 Version CAS；数据库不可用时 fail closed，不引入缓存、
 * MQ、Seata 或跨服务访问。偏好从不参与 Authorization 或业务事实计算。</p>
 */
@Service
public class SystemUserPreferenceApplicationService {
    private static final int MAX_VIEW_LIST = 100;
    private final UserPreferenceRepository repository;
    private final CurrentPreferenceUserProvider currentUserProvider;
    private final PreferencePayloadSizer payloadSizer;

    public SystemUserPreferenceApplicationService(
            UserPreferenceRepository repository,
            CurrentPreferenceUserProvider currentUserProvider,
            PreferencePayloadSizer payloadSizer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider");
        this.payloadSizer = Objects.requireNonNull(payloadSizer, "payloadSizer");
    }

    /** 无记录时返回 version=0、persisted=false 和冻结 Platform Default，无写副作用。 */
    @Transactional(readOnly = true)
    public ResolvedUserPreference getMyPreference() {
        String userId = currentUserProvider.requireUserId();
        return repository.findPreference(userId).map(SystemUserPreferenceApplicationService::resolve)
                .orElseGet(SystemUserPreferenceApplicationService::defaultPreference);
    }

    /** 全量替换五个可空显示覆盖；首次保存 version 必须为零。 */
    @Transactional
    public ResolvedUserPreference saveMyPreference(SaveDisplayPreferenceCommand command) {
        return validated(() -> doSaveMyPreference(command));
    }

    private ResolvedUserPreference doSaveMyPreference(SaveDisplayPreferenceCommand command) {
        Objects.requireNonNull(command, "command");
        long version = requireVersion(command.version());
        String userId = currentUserProvider.requireUserId();
        SupportedLocale locale = nullable(command.locale(), SupportedLocale::parse);
        DisplayTimezone timezone = nullable(command.displayTimezone(), DisplayTimezone::new);
        ThemeMode theme = nullable(command.themeMode(), ThemeMode::parse);
        Density density = nullable(command.density(), Density::parse);
        PageSize pageSize = command.pageSize() == null ? null : PageSize.parse(command.pageSize());

        UserPreference saved = repository.findPreference(userId).map(current -> {
            UserPreference changed = current.replace(version, locale, timezone, theme, density, pageSize);
            if (!repository.updatePreference(changed)) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return repository.findPreference(userId).orElseThrow(IllegalStateException::new);
        }).orElseGet(() -> {
            if (version != 0) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return repository.insertPreference(UserPreference.create(
                    userId, locale, timezone, theme, density, pageSize));
        });
        return resolve(saved);
    }

    /** 清空已有显示覆盖但保留记录；无记录且 version=0 时保持未持久化默认值。 */
    @Transactional
    public ResolvedUserPreference resetMyPreference(ResetCommand command) {
        return validated(() -> doResetMyPreference(command));
    }

    private ResolvedUserPreference doResetMyPreference(ResetCommand command) {
        Objects.requireNonNull(command, "command");
        long version = requireVersion(command.version());
        String userId = currentUserProvider.requireUserId();
        return repository.findPreference(userId).map(current -> {
            if (!repository.updatePreference(current.reset(version))) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return resolve(repository.findPreference(userId).orElseThrow(IllegalStateException::new));
        }).orElseGet(() -> {
            if (version != 0) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return defaultPreference();
        });
    }

    /** 读取当前用户指定稳定双 Code 的视图；禁用或无记录时返回默认空设置。 */
    @Transactional(readOnly = true)
    public io.github.chrisshi.mom.system.api.UserViewSetting getMyView(String applicationCode, String viewKey) {
        return validated(() -> doGetMyView(applicationCode, viewKey));
    }

    private io.github.chrisshi.mom.system.api.UserViewSetting doGetMyView(
            String applicationCode, String viewKey) {
        String app = PreferenceRules.requireApplicationCode(applicationCode);
        String view = PreferenceRules.requireViewKey(viewKey);
        String userId = currentUserProvider.requireUserId();
        return repository.findView(userId, app, view).map(SystemUserPreferenceApplicationService::toApiView)
                .orElseGet(() -> defaultView(app, view));
    }

    /** 保存当前用户类型化视图；首次保存 version 必须为零，再次保存会重新启用 Reset 记录。 */
    @Transactional
    public io.github.chrisshi.mom.system.api.UserViewSetting saveMyView(
            String applicationCode, String viewKey, SaveViewSettingCommand command) {
        return validated(() -> doSaveMyView(applicationCode, viewKey, command));
    }

    private io.github.chrisshi.mom.system.api.UserViewSetting doSaveMyView(
            String applicationCode, String viewKey, SaveViewSettingCommand command) {
        Objects.requireNonNull(command, "command");
        String app = PreferenceRules.requireApplicationCode(applicationCode);
        String view = PreferenceRules.requireViewKey(viewKey);
        long version = requireVersion(command.version());
        int schemaVersion = requireSchemaVersion(command.schemaVersion());
        List<ColumnSetting> columns = requireList(command.columns(), "invalid_column_setting").stream()
                .map(SystemUserPreferenceApplicationService::toColumn).toList();
        List<SortSetting> sorts = requireList(command.sorts(), "invalid_sort_setting").stream()
                .map(SystemUserPreferenceApplicationService::toSort).toList();
        List<FilterSetting> filters = requireList(command.filters(), "invalid_filter_setting").stream()
                .map(SystemUserPreferenceApplicationService::toFilter).toList();
        PreferenceRules.validateView(columns, sorts, filters);
        if (payloadSizer.encodedBytes(columns, sorts, filters) > PreferenceRules.MAX_PAYLOAD_BYTES) {
            throw new io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException(
                    "payload_too_large", "视图设置 Payload 超过 16 KiB");
        }
        PageSize pageSize = command.pageSize() == null ? null : PageSize.parse(command.pageSize());
        String userId = currentUserProvider.requireUserId();

        UserViewSetting saved = repository.findView(userId, app, view).map(current -> {
            UserViewSetting changed = current.replace(
                    version, schemaVersion, columns, sorts, filters, pageSize);
            if (!repository.updateView(changed)) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return repository.findView(userId, app, view).orElseThrow(IllegalStateException::new);
        }).orElseGet(() -> {
            if (version != 0) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return repository.insertView(UserViewSetting.create(
                    userId, app, view, schemaVersion, columns, sorts, filters, pageSize));
        });
        return toApiView(saved);
    }

    /** Reset 当前用户视图：清空覆盖、禁用并递增版本，不物理删除。 */
    @Transactional
    public io.github.chrisshi.mom.system.api.UserViewSetting resetMyView(
            String applicationCode, String viewKey, ResetCommand command) {
        return validated(() -> doResetMyView(applicationCode, viewKey, command));
    }

    private io.github.chrisshi.mom.system.api.UserViewSetting doResetMyView(
            String applicationCode, String viewKey, ResetCommand command) {
        Objects.requireNonNull(command, "command");
        String app = PreferenceRules.requireApplicationCode(applicationCode);
        String view = PreferenceRules.requireViewKey(viewKey);
        long version = requireVersion(command.version());
        String userId = currentUserProvider.requireUserId();
        return repository.findView(userId, app, view).map(current -> {
            if (!repository.updateView(current.reset(version))) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return toApiView(repository.findView(userId, app, view).orElseThrow(IllegalStateException::new));
        }).orElseGet(() -> {
            if (version != 0) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            return defaultView(app, view);
        });
    }

    /** 列出当前用户在指定应用内最多 100 个已启用视图，固定按 viewKey/id 排序。 */
    @Transactional(readOnly = true)
    public List<io.github.chrisshi.mom.system.api.UserViewSetting> listMyViews(String applicationCode) {
        return validated(() -> doListMyViews(applicationCode));
    }

    private List<io.github.chrisshi.mom.system.api.UserViewSetting> doListMyViews(String applicationCode) {
        String app = PreferenceRules.requireApplicationCode(applicationCode);
        String userId = currentUserProvider.requireUserId();
        return repository.findViews(userId, app, MAX_VIEW_LIST).stream()
                .map(SystemUserPreferenceApplicationService::toApiView).toList();
    }

    private static ResolvedUserPreference resolve(UserPreference preference) {
        var source = ResolvedUserPreference.Source.USER;
        var fallback = ResolvedUserPreference.Source.PLATFORM_DEFAULT;
        return new ResolvedUserPreference(
                preference.locale() == null ? "zh-CN" : preference.locale().tag(),
                preference.displayTimezone() == null ? "UTC" : preference.displayTimezone().value(),
                preference.themeMode() == null ? UserThemeMode.SYSTEM : UserThemeMode.valueOf(preference.themeMode().name()),
                preference.density() == null ? UserDensity.COMFORTABLE : UserDensity.valueOf(preference.density().name()),
                preference.pageSize() == null ? 20 : preference.pageSize().value(),
                preference.version(), true, preference.updatedAt(),
                new ResolvedUserPreference.Sources(
                        preference.locale() == null ? fallback : source,
                        preference.displayTimezone() == null ? fallback : source,
                        preference.themeMode() == null ? fallback : source,
                        preference.density() == null ? fallback : source,
                        preference.pageSize() == null ? fallback : source));
    }

    private static ResolvedUserPreference defaultPreference() {
        var fallback = ResolvedUserPreference.Source.PLATFORM_DEFAULT;
        return new ResolvedUserPreference("zh-CN", "UTC", UserThemeMode.SYSTEM, UserDensity.COMFORTABLE,
                20, 0, false, null, new ResolvedUserPreference.Sources(
                        fallback, fallback, fallback, fallback, fallback));
    }

    private static io.github.chrisshi.mom.system.api.UserViewSetting toApiView(UserViewSetting setting) {
        List<io.github.chrisshi.mom.system.api.UserViewSetting.ColumnSetting> columns = setting.enabled()
                ? setting.columns().stream().map(value -> new io.github.chrisshi.mom.system.api.UserViewSetting.ColumnSetting(
                        value.columnKey(), value.visible(), value.order(), value.width(),
                        io.github.chrisshi.mom.system.api.UserViewSetting.Pinned.valueOf(value.pinned().name()))).toList()
                : List.of();
        List<io.github.chrisshi.mom.system.api.UserViewSetting.SortSetting> sorts = setting.enabled()
                ? setting.sorts().stream().map(value -> new io.github.chrisshi.mom.system.api.UserViewSetting.SortSetting(
                        value.fieldKey(), io.github.chrisshi.mom.system.api.UserViewSetting.Direction.valueOf(
                                value.direction().name()), value.priority())).toList()
                : List.of();
        List<io.github.chrisshi.mom.system.api.UserViewSetting.FilterSetting> filters = setting.enabled()
                ? setting.filters().stream().map(value -> new io.github.chrisshi.mom.system.api.UserViewSetting.FilterSetting(
                        value.fieldKey(), io.github.chrisshi.mom.system.api.UserViewSetting.Operator.valueOf(
                                value.operator().name()), io.github.chrisshi.mom.system.api.UserViewSetting.ValueType.valueOf(
                                value.valueType().name()), value.values())).toList()
                : List.of();
        return new io.github.chrisshi.mom.system.api.UserViewSetting(
                setting.applicationCode(), setting.viewKey(), setting.schemaVersion(), columns, sorts, filters,
                setting.enabled() && setting.pageSize() != null ? setting.pageSize().value() : null,
                setting.enabled(), setting.version(), true, setting.updatedAt());
    }

    private static io.github.chrisshi.mom.system.api.UserViewSetting defaultView(String app, String view) {
        return new io.github.chrisshi.mom.system.api.UserViewSetting(
                app, view, 1, List.of(), List.of(), List.of(), null, false, 0, false, null);
    }

    private static ColumnSetting toColumn(ColumnCommand value) {
        if (value == null || value.visible() == null || value.order() == null || value.pinned() == null) {
            throw invalid("invalid_column_setting", "列设置字段不能为空");
        }
        try {
            return new ColumnSetting(value.columnKey(), value.visible(), value.order(), value.width(),
                    ColumnSetting.Pinned.valueOf(value.pinned()));
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_column_setting", "列设置非法", exception);
        }
    }

    private static SortSetting toSort(SortCommand value) {
        if (value == null || value.direction() == null || value.priority() == null) {
            throw invalid("invalid_sort_setting", "排序设置字段不能为空");
        }
        try {
            return new SortSetting(value.fieldKey(), SortSetting.Direction.valueOf(value.direction()), value.priority());
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_sort_setting", "排序设置非法", exception);
        }
    }

    private static FilterSetting toFilter(FilterCommand value) {
        if (value == null || value.operator() == null || value.valueType() == null || value.values() == null) {
            throw invalid("invalid_filter_setting", "Filter 设置字段不能为空");
        }
        try {
            return new FilterSetting(value.fieldKey(), FilterSetting.Operator.valueOf(value.operator()),
                    FilterSetting.ValueType.valueOf(value.valueType()), value.values());
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_filter_setting", "Filter 设置非法", exception);
        }
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 不能小于 0");
        }
        return version;
    }

    private static int requireSchemaVersion(Integer value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("schemaVersion 必须大于 0");
        }
        return value;
    }

    private static <T> List<T> requireList(List<T> values, String code) {
        if (values == null) {
            throw invalid(code, "视图设置数组不能为空");
        }
        return values;
    }

    private static <T> T nullable(String value, java.util.function.Function<String, T> parser) {
        return value == null ? null : parser.apply(value);
    }

    private static io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException invalid(
            String code, String message) {
        return new io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException(code, message);
    }

    private static io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException invalid(
            String code, String message, Throwable cause) {
        if (cause instanceof io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException validation) {
            return validation;
        }
        if (cause instanceof StalePreferenceVersionException) {
            throw new SystemUserPreferenceException.StaleVersion();
        }
        return new io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException(code, message, cause);
    }

    private static <T> T validated(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (io.github.chrisshi.mom.system.domain.preference.PreferenceValidationException exception) {
            throw new SystemUserPreferenceException.Invalid(exception.code(), exception.getMessage());
        } catch (StalePreferenceVersionException exception) {
            throw new SystemUserPreferenceException.StaleVersion();
        }
    }
}
