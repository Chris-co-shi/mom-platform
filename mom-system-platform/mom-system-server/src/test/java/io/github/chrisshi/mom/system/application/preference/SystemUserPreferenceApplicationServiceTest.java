package io.github.chrisshi.mom.system.application.preference;

import io.github.chrisshi.mom.system.api.ResolvedUserPreference;
import io.github.chrisshi.mom.system.domain.preference.ColumnSetting;
import io.github.chrisshi.mom.system.domain.preference.FilterSetting;
import io.github.chrisshi.mom.system.domain.preference.SortSetting;
import io.github.chrisshi.mom.system.domain.preference.UserPreference;
import io.github.chrisshi.mom.system.domain.preference.UserPreferenceRepository;
import io.github.chrisshi.mom.system.domain.preference.UserViewSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ColumnCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.FilterCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ResetCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveDisplayPreferenceCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveViewSettingCommand;
import static io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SortCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 当前用户隔离、默认解析、首次竞争、Version、Reset 和 View List 的 Application 测试。 */
class SystemUserPreferenceApplicationServiceTest {
    private final FakeRepository repository = new FakeRepository();
    private final MutableCurrentUser currentUser = new MutableCurrentUser("101");
    private SystemUserPreferenceApplicationService service;

    @BeforeEach
    void setUp() {
        repository.clear();
        currentUser.userId = "101";
        service = new SystemUserPreferenceApplicationService(repository, currentUser,
                (columns, sorts, filters) -> 256);
    }

    @Test
    void missingRecordMustReturnFrozenPlatformDefaultsAndSources() {
        ResolvedUserPreference result = service.getMyPreference();
        assertThat(result.locale()).isEqualTo("zh-CN");
        assertThat(result.displayTimezone()).isEqualTo("UTC");
        assertThat(result.themeMode().name()).isEqualTo("SYSTEM");
        assertThat(result.density().name()).isEqualTo("COMFORTABLE");
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.version()).isZero();
        assertThat(result.persisted()).isFalse();
        assertThat(result.sources().locale()).isEqualTo(ResolvedUserPreference.Source.PLATFORM_DEFAULT);
    }

    @Test
    void createUpdateRemoveOverrideResetAndStaleMustUseVersion() {
        ResolvedUserPreference created = service.saveMyPreference(
                new SaveDisplayPreferenceCommand("en-US", "Asia/Tokyo", "DARK", "COMPACT", 50, 0L));
        assertThat(created.persisted()).isTrue();
        assertThat(created.sources().locale()).isEqualTo(ResolvedUserPreference.Source.USER);

        ResolvedUserPreference updated = service.saveMyPreference(
                new SaveDisplayPreferenceCommand(null, "Europe/Prague", null, "COMPACT", null, 0L));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.locale()).isEqualTo("zh-CN");
        assertThat(updated.sources().locale()).isEqualTo(ResolvedUserPreference.Source.PLATFORM_DEFAULT);
        assertThatThrownBy(() -> service.saveMyPreference(
                new SaveDisplayPreferenceCommand(null, null, null, null, null, 0L)))
                .isInstanceOf(SystemUserPreferenceException.StaleVersion.class);

        ResolvedUserPreference reset = service.resetMyPreference(new ResetCommand(1L));
        assertThat(reset.version()).isEqualTo(2);
        assertThat(reset.persisted()).isTrue();
        assertThat(reset.sources().pageSize()).isEqualTo(ResolvedUserPreference.Source.PLATFORM_DEFAULT);
    }

    @Test
    void concurrentFirstCreateMustReturnStaleVersion() {
        repository.failNextPreferenceInsert = true;
        assertThatThrownBy(() -> service.saveMyPreference(
                new SaveDisplayPreferenceCommand("zh-CN", null, null, null, null, 0L)))
                .isInstanceOf(SystemUserPreferenceException.StaleVersion.class);
        assertThatThrownBy(() -> service.saveMyPreference(
                new SaveDisplayPreferenceCommand("zh-CN", null, null, null, null, 2L)))
                .isInstanceOf(SystemUserPreferenceException.StaleVersion.class);
    }

    @Test
    void usersMustOnlyReadAndWriteTheirOwnPreference() {
        service.saveMyPreference(new SaveDisplayPreferenceCommand("en-US", null, null, null, null, 0L));
        currentUser.userId = "202";
        assertThat(service.getMyPreference().persisted()).isFalse();
        service.saveMyPreference(new SaveDisplayPreferenceCommand("zh-CN", null, null, null, null, 0L));
        currentUser.userId = "101";
        assertThat(service.getMyPreference().locale()).isEqualTo("en-US");
        assertThat(repository.preferences).containsOnlyKeys("101", "202");
    }

    @Test
    void viewCreateReadUpdateResetDisabledDefaultAndListMustBeIsolated() {
        var created = service.saveMyView("mom-admin", "iam.users.list", viewCommand(0L, "display-name", 50));
        assertThat(created.enabled()).isTrue();
        assertThat(created.columns()).hasSize(1);
        assertThat(created.persisted()).isTrue();

        var updated = service.saveMyView("mom-admin", "iam.users.list", viewCommand(0L, "username", 100));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(service.listMyViews("mom-admin")).extracting(io.github.chrisshi.mom.system.api.UserViewSetting::viewKey)
                .containsExactly("iam.users.list");

        var reset = service.resetMyView("mom-admin", "iam.users.list", new ResetCommand(1L));
        assertThat(reset.enabled()).isFalse();
        assertThat(reset.columns()).isEmpty();
        assertThat(reset.version()).isEqualTo(2);
        assertThat(service.listMyViews("mom-admin")).isEmpty();
        assertThat(service.getMyView("mom-admin", "iam.users.list").persisted()).isTrue();

        currentUser.userId = "202";
        assertThat(service.getMyView("mom-admin", "iam.users.list").persisted()).isFalse();
        assertThat(service.listMyViews("mom-admin")).isEmpty();
    }

    @Test
    void viewStaleAndPayloadLimitMustFailBeforePersistence() {
        service.saveMyView("mom-admin", "iam.users.list", viewCommand(0L, "display-name", 20));
        assertThatThrownBy(() -> service.saveMyView(
                "mom-admin", "iam.users.list", viewCommand(2L, "username", 20)))
                .isInstanceOf(SystemUserPreferenceException.StaleVersion.class);

        service = new SystemUserPreferenceApplicationService(repository, currentUser,
                (columns, sorts, filters) -> 16 * 1024 + 1);
        assertThatThrownBy(() -> service.saveMyView(
                "mom-admin", "iam.groups.list", viewCommand(0L, "group-name", 20)))
                .isInstanceOf(SystemUserPreferenceException.Invalid.class)
                .extracting(exception -> ((SystemUserPreferenceException.Invalid) exception).code())
                .isEqualTo("payload_too_large");
    }

    private static SaveViewSettingCommand viewCommand(long version, String columnKey, int pageSize) {
        return new SaveViewSettingCommand(1,
                List.of(new ColumnCommand(columnKey, true, 0, 200, "NONE")),
                List.of(new SortCommand(columnKey, "ASC", 0)),
                List.of(new FilterCommand("enabled", "EQ", "BOOLEAN", List.of("true"))),
                pageSize, version);
    }

    private static final class MutableCurrentUser implements CurrentPreferenceUserProvider {
        private String userId;

        private MutableCurrentUser(String userId) {
            this.userId = userId;
        }

        @Override
        public String requireUserId() {
            return userId;
        }
    }

    private static final class FakeRepository implements UserPreferenceRepository {
        private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
        private final Map<String, UserPreference> preferences = new LinkedHashMap<>();
        private final Map<String, UserViewSetting> views = new LinkedHashMap<>();
        private boolean failNextPreferenceInsert;

        void clear() {
            preferences.clear();
            views.clear();
            failNextPreferenceInsert = false;
        }

        @Override
        public Optional<UserPreference> findPreference(String userId) {
            return Optional.ofNullable(preferences.get(userId));
        }

        @Override
        public UserPreference insertPreference(UserPreference value) {
            if (failNextPreferenceInsert || preferences.containsKey(value.userId())) {
                failNextPreferenceInsert = false;
                throw new SystemUserPreferenceException.StaleVersion();
            }
            UserPreference saved = new UserPreference("p-" + value.userId(), value.userId(), value.locale(),
                    value.displayTimezone(), value.themeMode(), value.density(), value.pageSize(), 0, NOW);
            preferences.put(value.userId(), saved);
            return saved;
        }

        @Override
        public boolean updatePreference(UserPreference value) {
            UserPreference current = preferences.get(value.userId());
            if (current == null || current.version() != value.version()) {
                return false;
            }
            preferences.put(value.userId(), new UserPreference(current.id(), current.userId(), value.locale(),
                    value.displayTimezone(), value.themeMode(), value.density(), value.pageSize(),
                    value.version() + 1, NOW.plusSeconds(value.version() + 1)));
            return true;
        }

        @Override
        public Optional<UserViewSetting> findView(String userId, String applicationCode, String viewKey) {
            return Optional.ofNullable(views.get(key(userId, applicationCode, viewKey)));
        }

        @Override
        public UserViewSetting insertView(UserViewSetting value) {
            String key = key(value.userId(), value.applicationCode(), value.viewKey());
            if (views.containsKey(key)) {
                throw new SystemUserPreferenceException.StaleVersion();
            }
            UserViewSetting saved = new UserViewSetting("v-" + views.size(), value.userId(),
                    value.applicationCode(), value.viewKey(), value.schemaVersion(), value.columns(), value.sorts(),
                    value.filters(), value.pageSize(), value.enabled(), 0, NOW);
            views.put(key, saved);
            return saved;
        }

        @Override
        public boolean updateView(UserViewSetting value) {
            String key = key(value.userId(), value.applicationCode(), value.viewKey());
            UserViewSetting current = views.get(key);
            if (current == null || current.version() != value.version()) {
                return false;
            }
            views.put(key, new UserViewSetting(current.id(), current.userId(), current.applicationCode(),
                    current.viewKey(), value.schemaVersion(), value.columns(), value.sorts(), value.filters(),
                    value.pageSize(), value.enabled(), value.version() + 1, NOW.plusSeconds(value.version() + 1)));
            return true;
        }

        @Override
        public List<UserViewSetting> findViews(String userId, String applicationCode, int limit) {
            return views.values().stream()
                    .filter(value -> value.userId().equals(userId)
                            && value.applicationCode().equals(applicationCode) && value.enabled())
                    .sorted(java.util.Comparator.comparing(UserViewSetting::viewKey))
                    .limit(limit).toList();
        }

        private static String key(String userId, String applicationCode, String viewKey) {
            return userId + "|" + applicationCode + "|" + viewKey;
        }
    }
}
