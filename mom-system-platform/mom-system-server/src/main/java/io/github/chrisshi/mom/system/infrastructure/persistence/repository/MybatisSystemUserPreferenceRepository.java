package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceException;
import io.github.chrisshi.mom.system.domain.preference.Density;
import io.github.chrisshi.mom.system.domain.preference.DisplayTimezone;
import io.github.chrisshi.mom.system.domain.preference.PageSize;
import io.github.chrisshi.mom.system.domain.preference.SupportedLocale;
import io.github.chrisshi.mom.system.domain.preference.ThemeMode;
import io.github.chrisshi.mom.system.domain.preference.UserPreference;
import io.github.chrisshi.mom.system.domain.preference.UserPreferenceRepository;
import io.github.chrisshi.mom.system.domain.preference.UserViewSetting;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserPreferenceEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserViewSettingEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemUserPreferenceMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemUserViewSettingMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的用户偏好 Repository Adapter。
 *
 * <p>两张表的普通 CRUD、唯一读取、固定排序列表和 Version CAS 全部使用 MomBaseMapper 与 Lambda Wrapper；
 * 没有 XML、注解 SQL 或 JDBC。唯一竞争被脱敏映射为 stale_version，所有查询强制包含 JWT sub 用户条件。</p>
 */
@Repository
public class MybatisSystemUserPreferenceRepository implements UserPreferenceRepository {
    private final SystemUserPreferenceMapper preferenceMapper;
    private final SystemUserViewSettingMapper viewMapper;
    private final SystemUserViewJsonCodec codec;

    public MybatisSystemUserPreferenceRepository(
            SystemUserPreferenceMapper preferenceMapper,
            SystemUserViewSettingMapper viewMapper,
            SystemUserViewJsonCodec codec) {
        this.preferenceMapper = preferenceMapper;
        this.viewMapper = viewMapper;
        this.codec = codec;
    }

    @Override
    public Optional<UserPreference> findPreference(String userId) {
        var query = Wrappers.<SystemUserPreferenceEntity>lambdaQuery()
                .eq(SystemUserPreferenceEntity::getUserId, userId);
        return Optional.ofNullable(preferenceMapper.selectOne(query)).map(MybatisSystemUserPreferenceRepository::toDomain);
    }

    @Override
    public UserPreference insertPreference(UserPreference preference) {
        SystemUserPreferenceEntity entity = toNewEntity(preference);
        try {
            if (preferenceMapper.insert(entity) != 1) {
                throw new IllegalStateException("用户偏好未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemUserPreferenceException.StaleVersion(exception);
        }
        return findPreference(preference.userId()).orElseThrow(IllegalStateException::new);
    }

    @Override
    public boolean updatePreference(UserPreference preference) {
        SystemUserPreferenceEntity entity = toUpdateEntity(preference);
        return preferenceMapper.updateById(entity) == 1;
    }

    @Override
    public Optional<UserViewSetting> findView(String userId, String applicationCode, String viewKey) {
        var query = Wrappers.<SystemUserViewSettingEntity>lambdaQuery()
                .eq(SystemUserViewSettingEntity::getUserId, userId)
                .eq(SystemUserViewSettingEntity::getApplicationCode, applicationCode)
                .eq(SystemUserViewSettingEntity::getViewKey, viewKey);
        return Optional.ofNullable(viewMapper.selectOne(query)).map(this::toDomain);
    }

    @Override
    public UserViewSetting insertView(UserViewSetting setting) {
        SystemUserViewSettingEntity entity = toNewEntity(setting);
        try {
            if (viewMapper.insert(entity) != 1) {
                throw new IllegalStateException("用户视图未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemUserPreferenceException.StaleVersion(exception);
        }
        return findView(setting.userId(), setting.applicationCode(), setting.viewKey())
                .orElseThrow(IllegalStateException::new);
    }

    @Override
    public boolean updateView(UserViewSetting setting) {
        return viewMapper.updateById(toUpdateEntity(setting)) == 1;
    }

    @Override
    public List<UserViewSetting> findViews(String userId, String applicationCode, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("视图列表上限必须在 1～100 之间");
        }
        return viewMapper.selectList(Wrappers.<SystemUserViewSettingEntity>lambdaQuery()
                        .eq(SystemUserViewSettingEntity::getUserId, userId)
                        .eq(SystemUserViewSettingEntity::getApplicationCode, applicationCode)
                        .eq(SystemUserViewSettingEntity::getEnabled, true)
                        .orderByAsc(SystemUserViewSettingEntity::getViewKey)
                        .orderByAsc(SystemUserViewSettingEntity::getId)
                        .last("LIMIT " + limit))
                .stream().map(this::toDomain).toList();
    }

    private static SystemUserPreferenceEntity toNewEntity(UserPreference value) {
        SystemUserPreferenceEntity entity = new SystemUserPreferenceEntity();
        entity.setUserId(value.userId());
        copy(value, entity);
        return entity;
    }

    private static SystemUserPreferenceEntity toUpdateEntity(UserPreference value) {
        SystemUserPreferenceEntity entity = new SystemUserPreferenceEntity();
        entity.setId(value.id());
        entity.setVersion(value.version());
        copy(value, entity);
        return entity;
    }

    private static void copy(UserPreference value, SystemUserPreferenceEntity entity) {
        entity.setLocale(value.locale() == null ? null : value.locale().tag());
        entity.setDisplayTimezone(value.displayTimezone() == null ? null : value.displayTimezone().value());
        entity.setThemeMode(value.themeMode() == null ? null : value.themeMode().name());
        entity.setDensity(value.density() == null ? null : value.density().name());
        entity.setPageSize(value.pageSize() == null ? null : value.pageSize().value());
    }

    private SystemUserViewSettingEntity toNewEntity(UserViewSetting value) {
        SystemUserViewSettingEntity entity = new SystemUserViewSettingEntity();
        entity.setUserId(value.userId());
        entity.setApplicationCode(value.applicationCode());
        entity.setViewKey(value.viewKey());
        copy(value, entity);
        return entity;
    }

    private SystemUserViewSettingEntity toUpdateEntity(UserViewSetting value) {
        SystemUserViewSettingEntity entity = new SystemUserViewSettingEntity();
        entity.setId(value.id());
        entity.setVersion(value.version());
        copy(value, entity);
        return entity;
    }

    private void copy(UserViewSetting value, SystemUserViewSettingEntity entity) {
        entity.setSchemaVersion(value.schemaVersion());
        entity.setColumnsJson(codec.encodeColumns(value.columns()));
        entity.setSortJson(codec.encodeSorts(value.sorts()));
        entity.setFiltersJson(codec.encodeFilters(value.filters()));
        entity.setPageSize(value.pageSize() == null ? null : value.pageSize().value());
        entity.setEnabled(value.enabled());
    }

    private static UserPreference toDomain(SystemUserPreferenceEntity entity) {
        return new UserPreference(entity.getId(), entity.getUserId(),
                entity.getLocale() == null ? null : SupportedLocale.parse(entity.getLocale()),
                entity.getDisplayTimezone() == null ? null : new DisplayTimezone(entity.getDisplayTimezone()),
                entity.getThemeMode() == null ? null : ThemeMode.parse(entity.getThemeMode()),
                entity.getDensity() == null ? null : Density.parse(entity.getDensity()),
                entity.getPageSize() == null ? null : PageSize.parse(entity.getPageSize()),
                entity.getVersion(), entity.getUpdatedAt());
    }

    private UserViewSetting toDomain(SystemUserViewSettingEntity entity) {
        return new UserViewSetting(entity.getId(), entity.getUserId(), entity.getApplicationCode(), entity.getViewKey(),
                entity.getSchemaVersion(), codec.decodeColumns(entity.getColumnsJson()),
                codec.decodeSorts(entity.getSortJson()), codec.decodeFilters(entity.getFiltersJson()),
                entity.getPageSize() == null ? null : PageSize.parse(entity.getPageSize()),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getUpdatedAt());
    }
}
