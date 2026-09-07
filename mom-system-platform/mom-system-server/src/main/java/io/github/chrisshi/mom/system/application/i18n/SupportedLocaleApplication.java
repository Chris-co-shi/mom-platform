package io.github.chrisshi.mom.system.application.i18n;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.api.SupportedLocaleInfo;
import io.github.chrisshi.mom.system.application.SystemV1Exception;
import io.github.chrisshi.mom.system.application.SystemV1Rules;
import io.github.chrisshi.mom.system.infrastructure.entity.SupportedLocaleEntity;
import io.github.chrisshi.mom.system.infrastructure.mapper.SupportedLocaleMapper;
import io.github.chrisshi.mom.webmvc.i18n.I18nChangeNotifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.github.chrisshi.mom.system.application.i18n.I18nModels.ChangeLocaleStatus;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateLocale;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.LocaleResolution;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.LocaleView;
import static io.github.chrisshi.mom.system.application.i18n.I18nModels.UpdateLocale;

/**
 * System SupportedLocale 的用例、事务与唯一默认规则边界。
 *
 * <p>Application 直接依赖本上下文 Mapper，属于 ADR-042 Level 1。新增 Locale 默认不是平台 Default；默认
 * 切换在单 PostgreSQL 事务中锁定 Locale 集合，先撤销旧默认再设置新默认，并由部分唯一索引最终兜底。
 * 所有通知由 Framework 在 after-commit 广播；SSE 故障不回滚数据库，数据库故障时不降级为本地假数据。</p>
 */
@Service
public class SupportedLocaleApplication {
    private final SupportedLocaleMapper localeMapper;
    private final I18nChangeNotifier notifier;

    /**
     * @param localeMapper SupportedLocale 单表 Mapper
     * @param notifier Framework after-commit 本地通知器
     */
    public SupportedLocaleApplication(SupportedLocaleMapper localeMapper, I18nChangeNotifier notifier) {
        this.localeMapper = localeMapper;
        this.notifier = notifier;
    }

    /** 创建动态 Locale；创建默认 Locale 必须使用单独的默认切换用例。 */
    @Transactional
    public LocaleView create(CreateLocale command) {
        SupportedLocaleEntity entity = new SupportedLocaleEntity();
        entity.setLocaleCode(SystemV1Rules.localeCode(command.localeCode()));
        entity.setDisplayName(SystemV1Rules.displayText(command.displayName(), "displayName", 100));
        entity.setNativeName(SystemV1Rules.displayText(command.nativeName(), "nativeName", 100));
        entity.setEnabled(command.enabled() == null || command.enabled());
        entity.setDefaultLocale(false);
        entity.setSortOrder(SystemV1Rules.sortOrder(command.sortOrder()));
        try {
            localeMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemV1Exception.Conflict("locale_code_conflict", "system.locale.error.code_conflict");
        }
        notifier.localesChanged();
        return toView(entity);
    }

    /** 更新 Locale 展示名和排序，不允许修改跨服务稳定 localeCode。 */
    @Transactional
    public LocaleView update(String id, UpdateLocale command) {
        SupportedLocaleEntity entity = requireById(id);
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setDisplayName(SystemV1Rules.displayText(command.displayName(), "displayName", 100));
        entity.setNativeName(SystemV1Rules.displayText(command.nativeName(), "nativeName", 100));
        entity.setSortOrder(SystemV1Rules.sortOrder(command.sortOrder()));
        updateOrConflict(entity);
        notifier.localesChanged();
        return toView(requireById(id));
    }

    /** 启停 Locale；平台默认 Locale 不允许直接禁用。 */
    @Transactional
    public LocaleView changeStatus(String id, ChangeLocaleStatus command) {
        if (command.enabled() == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        SupportedLocaleEntity entity = requireById(id);
        if (Boolean.TRUE.equals(entity.getDefaultLocale()) && !command.enabled()) {
            throw new SystemV1Exception.Conflict(
                    "default_locale_cannot_disable", "system.locale.error.default_cannot_disable");
        }
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setEnabled(command.enabled());
        updateOrConflict(entity);
        notifier.localesChanged();
        return toView(requireById(id));
    }

    /**
     * 将已启用 Locale 切换为全平台唯一默认值。
     *
     * @param id 目标 Locale 技术 ID
     * @param version 目标 Locale 当前版本
     * @return 切换后的目标 Locale
     */
    @Transactional
    public LocaleView makeDefault(String id, Long version) {
        List<SupportedLocaleEntity> locales = localeMapper.selectList(
                Wrappers.<SupportedLocaleEntity>lambdaQuery()
                        .orderByAsc(SupportedLocaleEntity::getId)
                        .last("FOR UPDATE"));
        SupportedLocaleEntity target = locales.stream()
                .filter(locale -> locale.getId().equals(SystemV1Rules.id(id)))
                .findFirst()
                .orElseThrow(() -> new SystemV1Exception.NotFound("system.locale.error.not_found"));
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new SystemV1Exception.Conflict(
                    "default_locale_must_enable", "system.locale.error.default_must_enable");
        }
        if (target.getVersion() != SystemV1Rules.version(version)) {
            throw new SystemV1Exception.Conflict("stale_version", "system.locale.error.stale_version");
        }
        // PostgreSQL 唯一索引按每条 UPDATE 立即检查；必须先撤销旧默认，再设置目标，不能依赖查询排序碰运气。
        for (SupportedLocaleEntity locale : locales) {
            if (!locale.getId().equals(target.getId()) && Boolean.TRUE.equals(locale.getDefaultLocale())) {
                locale.setDefaultLocale(false);
                updateOrConflict(locale);
            }
        }
        if (!Boolean.TRUE.equals(target.getDefaultLocale())) {
            target.setDefaultLocale(true);
            updateOrConflict(target);
        }
        notifier.localesChanged();
        return toView(requireById(id));
    }

    /** 返回固定 sortOrder/localeCode/id 排序的全部 Locale 管理列表。 */
    @Transactional(readOnly = true)
    public List<LocaleView> list() {
        return localeMapper.selectList(Wrappers.<SupportedLocaleEntity>lambdaQuery()
                        .orderByAsc(SupportedLocaleEntity::getSortOrder)
                        .orderByAsc(SupportedLocaleEntity::getLocaleCode)
                        .orderByAsc(SupportedLocaleEntity::getId))
                .stream().map(SupportedLocaleApplication::toView).toList();
    }

    /** 返回供跨服务或客户端读取的稳定 Locale Code 契约。 */
    @Transactional(readOnly = true)
    public List<SupportedLocaleInfo> supportedLocales() {
        return list().stream().map(locale -> new SupportedLocaleInfo(
                locale.localeCode(), locale.displayName(), locale.nativeName(), locale.enabled(),
                locale.defaultLocale(), locale.sortOrder())).toList();
    }

    /**
     * 解析请求 Locale；不存在或禁用时回退全平台默认 Locale。
     *
     * @param requestedLocale 请求 BCP 47 Tag
     * @return 请求、实际和默认 Locale
     */
    @Transactional(readOnly = true)
    public LocaleResolution resolve(String requestedLocale) {
        String requested = SystemV1Rules.localeCode(requestedLocale);
        SupportedLocaleEntity defaultLocale = requireDefault();
        SupportedLocaleEntity requestedEntity = localeMapper.selectOne(
                Wrappers.<SupportedLocaleEntity>lambdaQuery()
                        .eq(SupportedLocaleEntity::getLocaleCode, requested));
        String effective = requestedEntity != null && Boolean.TRUE.equals(requestedEntity.getEnabled())
                ? requestedEntity.getLocaleCode() : defaultLocale.getLocaleCode();
        return new LocaleResolution(requested, effective, defaultLocale.getLocaleCode());
    }

    /** 校验 Translation 引用的 Locale 存在；禁用 Locale 的历史 Translation 仍允许维护。 */
    @Transactional(readOnly = true)
    public SupportedLocaleEntity requireLocale(String localeCode) {
        SupportedLocaleEntity entity = localeMapper.selectOne(Wrappers.<SupportedLocaleEntity>lambdaQuery()
                .eq(SupportedLocaleEntity::getLocaleCode, SystemV1Rules.localeCode(localeCode)));
        if (entity == null) {
            throw new SystemV1Exception.NotFound("system.locale.error.not_found");
        }
        return entity;
    }

    private SupportedLocaleEntity requireDefault() {
        SupportedLocaleEntity entity = localeMapper.selectOne(Wrappers.<SupportedLocaleEntity>lambdaQuery()
                .eq(SupportedLocaleEntity::getDefaultLocale, true)
                .eq(SupportedLocaleEntity::getEnabled, true));
        if (entity == null) {
            throw new IllegalStateException("System 缺少已启用的默认 Locale");
        }
        return entity;
    }

    private SupportedLocaleEntity requireById(String id) {
        SupportedLocaleEntity entity = localeMapper.selectById(SystemV1Rules.id(id));
        if (entity == null) {
            throw new SystemV1Exception.NotFound("system.locale.error.not_found");
        }
        return entity;
    }

    private void updateOrConflict(SupportedLocaleEntity entity) {
        try {
            if (localeMapper.updateById(entity) != 1) {
                throw new SystemV1Exception.Conflict("stale_version", "system.locale.error.stale_version");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemV1Exception.Conflict(
                    "default_locale_conflict", "system.locale.error.default_conflict");
        }
    }

    private static LocaleView toView(SupportedLocaleEntity entity) {
        return new LocaleView(entity.getId(), entity.getLocaleCode(), entity.getDisplayName(),
                entity.getNativeName(), Boolean.TRUE.equals(entity.getEnabled()),
                Boolean.TRUE.equals(entity.getDefaultLocale()), entity.getSortOrder(), entity.getVersion(),
                entity.getUpdatedAt());
    }
}
