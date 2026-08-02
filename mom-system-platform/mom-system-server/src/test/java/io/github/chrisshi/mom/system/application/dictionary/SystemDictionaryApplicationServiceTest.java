package io.github.chrisshi.mom.system.application.dictionary;

import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItem;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateDictionaryCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateItemCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageQuery;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageQuery;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateDictionaryCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateItemCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** System Dictionary 的事务编排、版本、启停、Active List 与兼容读取单元测试。 */
class SystemDictionaryApplicationServiceTest {
    private InMemoryDictionaryRepository dictionaries;
    private InMemoryItemRepository items;
    private SystemDictionaryApplicationService service;

    @BeforeEach
    void setUp() {
        dictionaries = new InMemoryDictionaryRepository();
        items = new InMemoryItemRepository();
        service = new SystemDictionaryApplicationService(dictionaries, items);
    }

    @Test
    void shouldCreateDictionaryWithCanonicalCodeAndServerAudit() {
        var created = createDictionary(" System.Common.State ", "Common State", true);
        assertThat(created.dictionaryCode()).isEqualTo("system.common.state");
        assertThat(created.createdBy()).isEqualTo("s14-test-actor");
        assertThat(created.version()).isZero();
    }

    @Test
    void duplicateDictionaryCodeMustConflict() {
        createDictionary("system.common.state", "State", true);
        assertThatThrownBy(() -> createDictionary("SYSTEM.COMMON.STATE", "Again", true))
                .isInstanceOf(SystemDictionaryException.Conflict.class);
    }

    @Test
    void dictionaryUpdateAndStatusMustUseOptimisticVersionWithoutChangingCode() {
        var created = createDictionary("system.common.state", "State", true);
        var updated = service.updateDictionary(created.id(),
                new UpdateDictionaryCommand("Display State", "changed", created.version()));
        assertThat(updated.dictionaryCode()).isEqualTo(created.dictionaryCode());
        assertThat(updated.dictionaryName()).isEqualTo("Display State");
        assertThat(updated.version()).isEqualTo(1L);
        assertThatThrownBy(() -> service.updateDictionary(created.id(),
                new UpdateDictionaryCommand("Stale", null, created.version())))
                .isInstanceOf(SystemDictionaryException.StaleVersion.class);

        var disabled = service.changeDictionaryStatus(created.id(), new StatusCommand(false, updated.version()));
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.version()).isEqualTo(2L);
    }

    @Test
    void itemCreationMustRequireParentAndEnforceCodeUniqueness() {
        assertThatThrownBy(() -> service.createItem("missing", item("ready", "Ready", 10, true)))
                .isInstanceOf(SystemDictionaryException.NotFound.class);
        var dictionary = createDictionary("system.common.state", "State", true);
        var created = service.createItem(dictionary.id(), item("Ready_State", "Ready", 10, true));
        assertThat(created.itemCode()).isEqualTo("ready_state");
        assertThat(created.createdBy()).isEqualTo("s14-test-actor");
        assertThatThrownBy(() -> service.createItem(dictionary.id(), item("READY_STATE", "Again", 20, true)))
                .isInstanceOf(SystemDictionaryException.Conflict.class);
    }

    @Test
    void itemUpdateAndStatusMustUseOptimisticVersionWithoutChangingReference() {
        var dictionary = createDictionary("system.common.state", "State", true);
        var created = service.createItem(dictionary.id(), item("ready", "Ready", 20, true));
        var updated = service.updateItem(dictionary.id(), created.id(),
                new UpdateItemCommand("Ready for display", 5, "changed", created.version()));
        assertThat(updated.dictionaryId()).isEqualTo(dictionary.id());
        assertThat(updated.itemCode()).isEqualTo("ready");
        assertThat(updated.itemLabel()).isEqualTo("Ready for display");
        assertThat(updated.sortOrder()).isEqualTo(5);
        assertThatThrownBy(() -> service.updateItem(dictionary.id(), created.id(),
                new UpdateItemCommand("Stale", 1, null, created.version())))
                .isInstanceOf(SystemDictionaryException.StaleVersion.class);

        var disabled = service.changeItemStatus(dictionary.id(), created.id(),
                new StatusCommand(false, updated.version()));
        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void activeListMustFilterAndUseFixedSort() {
        var dictionary = createDictionary("system.common.state", "State", true);
        service.createItem(dictionary.id(), item("zeta", "Zeta", 10, true));
        service.createItem(dictionary.id(), item("alpha", "Alpha", 10, true));
        service.createItem(dictionary.id(), item("disabled", "Disabled", 0, false));
        service.createItem(dictionary.id(), item("first", "First", 1, true));

        assertThat(service.activeItems("SYSTEM.COMMON.STATE"))
                .extracting(option -> option.itemCode())
                .containsExactly("first", "alpha", "zeta");
    }

    @Test
    void disablingDictionaryMustHideActiveListWithoutMutatingItems() {
        var dictionary = createDictionary("system.common.state", "State", true);
        var item = service.createItem(dictionary.id(), item("ready", "Ready", 10, true));
        service.changeDictionaryStatus(dictionary.id(), new StatusCommand(false, dictionary.version()));

        assertThat(service.activeItems("system.common.state")).isEmpty();
        assertThat(service.getItem(dictionary.id(), item.id()).enabled()).isTrue();
    }

    @Test
    void compatibilityReadMustReturnDisabledItemAndDictionary() {
        var dictionary = createDictionary("system.common.state", "State", true);
        var item = service.createItem(dictionary.id(), item("ready", "Ready", 10, true));
        var disabledItem = service.changeItemStatus(dictionary.id(), item.id(),
                new StatusCommand(false, item.version()));

        var itemCompatibility = service.resolveItem("system.common.state", "ready");
        assertThat(itemCompatibility.itemEnabled()).isFalse();
        assertThat(itemCompatibility.dictionaryEnabled()).isTrue();
        assertThat(itemCompatibility.effectiveEnabled()).isFalse();

        service.changeDictionaryStatus(dictionary.id(), new StatusCommand(false, dictionary.version()));
        var dictionaryCompatibility = service.resolveItem("system.common.state", "ready");
        assertThat(dictionaryCompatibility.dictionaryEnabled()).isFalse();
        assertThat(dictionaryCompatibility.itemEnabled()).isEqualTo(disabledItem.enabled());
        assertThat(dictionaryCompatibility.effectiveEnabled()).isFalse();
    }

    @Test
    void missingCompatibilityReferenceMustReturnNotFound() {
        assertThatThrownBy(() -> service.resolveItem("system.common.missing", "ready"))
                .isInstanceOf(SystemDictionaryException.NotFound.class);
        var dictionary = createDictionary("system.common.state", "State", true);
        assertThatThrownBy(() -> service.resolveItem(dictionary.dictionaryCode(), "missing"))
                .isInstanceOf(SystemDictionaryException.NotFound.class);
    }

    @Test
    void adminPagesMustBeBoundedAndExactlyFiltered() {
        var one = createDictionary("system.common.one", "One", true);
        createDictionary("system.common.two", "Two", false);
        service.createItem(one.id(), item("b", "B", 2, true));
        service.createItem(one.id(), item("a", "A", 1, false));

        assertThat(service.pageDictionaries(new DictionaryPageQuery("SYSTEM.COMMON.ONE", null, 0, 20)).items())
                .extracting(view -> view.dictionaryCode()).containsExactly("system.common.one");
        assertThat(service.pageItems(one.id(), new ItemPageQuery(null, null, 0, 20)).items())
                .extracting(view -> view.itemCode()).containsExactly("a", "b");
        assertThatThrownBy(() -> service.pageDictionaries(
                new DictionaryPageQuery(null, null, 0, 101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientCommandsMustNotExposeAuditOrImmutableReferenceUpdates() {
        assertThat(CreateDictionaryCommand.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("createdBy", "updatedBy", "operator");
        assertThat(UpdateDictionaryCommand.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("dictionaryCode", "createdBy", "updatedBy", "operator");
        assertThat(UpdateItemCommand.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("dictionaryId", "itemCode", "createdBy", "updatedBy", "operator");

        var dictionary = createDictionary("system.common.required", "Required", true);
        assertThatThrownBy(() -> service.updateDictionary(dictionary.id(),
                new UpdateDictionaryCommand("Missing Version", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeDictionaryStatus(dictionary.id(),
                new StatusCommand(null, dictionary.version())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createItem(dictionary.id(),
                new CreateItemCommand("missing-order", "Missing order", null, null, true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SystemDictionaryApplicationModels.DictionaryView createDictionary(
            String code, String name, boolean enabled) {
        return service.createDictionary(new CreateDictionaryCommand(code, name, null, enabled));
    }

    private static CreateItemCommand item(String code, String label, int order, boolean enabled) {
        return new CreateItemCommand(code, label, order, null, enabled);
    }

    /** 无框架内存字典 Port，只验证 Application 编排，不模拟真实数据库事务。 */
    private static final class InMemoryDictionaryRepository implements SystemDictionaryRepository {
        private final Map<String, SystemDictionary> values = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<SystemDictionary> findById(String id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<SystemDictionary> findByCode(String dictionaryCode) {
            return values.values().stream()
                    .filter(value -> value.dictionaryCode().equals(dictionaryCode)).findFirst();
        }

        @Override
        public SystemDictionary insert(SystemDictionary dictionary) {
            if (findByCode(dictionary.dictionaryCode()).isPresent()) {
                throw new SystemDictionaryException.Conflict("duplicate");
            }
            String id = "d" + ++sequence;
            Instant now = Instant.parse("2026-07-30T00:00:00Z");
            SystemDictionary persisted = new SystemDictionary(id, dictionary.dictionaryCode(),
                    dictionary.dictionaryName(), dictionary.enabled(), 0L, dictionary.description(),
                    "s14-test-actor", now, "s14-test-actor", now);
            values.put(id, persisted);
            return persisted;
        }

        @Override
        public boolean update(SystemDictionary dictionary) {
            SystemDictionary current = values.get(dictionary.id());
            if (current == null || current.version() != dictionary.version()) {
                return false;
            }
            values.put(current.id(), persisted(current, dictionary.dictionaryName(), current.enabled(),
                    dictionary.description()));
            return true;
        }

        @Override
        public boolean updateStatus(SystemDictionary dictionary) {
            SystemDictionary current = values.get(dictionary.id());
            if (current == null || current.version() != dictionary.version()) {
                return false;
            }
            values.put(current.id(), persisted(current, current.dictionaryName(), dictionary.enabled(),
                    current.description()));
            return true;
        }

        @Override
        public DictionaryPage findPage(DictionaryQuery query) {
            List<SystemDictionary> filtered = values.values().stream()
                    .filter(value -> query.dictionaryCode() == null
                            || value.dictionaryCode().equals(query.dictionaryCode()))
                    .filter(value -> query.enabled() == null || value.enabled() == query.enabled())
                    .sorted(Comparator.comparing(SystemDictionary::dictionaryCode)
                            .thenComparing(SystemDictionary::id)).toList();
            int from = Math.min(query.page() * query.size(), filtered.size());
            int to = Math.min(from + query.size(), filtered.size());
            return new DictionaryPage(filtered.subList(from, to), filtered.size(), query.page(), query.size());
        }

        private static SystemDictionary persisted(
                SystemDictionary current, String name, boolean enabled, String description) {
            return new SystemDictionary(current.id(), current.dictionaryCode(), name, enabled,
                    current.version() + 1, description, current.createdBy(), current.createdAt(),
                    "s14-test-actor", current.updatedAt().plusSeconds(1));
        }
    }

    /** 无框架内存 Item Port；固定排序与过滤模拟 Repository 契约。 */
    private static final class InMemoryItemRepository implements SystemDictionaryItemRepository {
        private final Map<String, SystemDictionaryItem> values = new LinkedHashMap<>();
        private long sequence;

        @Override
        public Optional<SystemDictionaryItem> findById(String dictionaryId, String itemId) {
            return Optional.ofNullable(values.get(itemId))
                    .filter(value -> value.dictionaryId().equals(dictionaryId));
        }

        @Override
        public Optional<SystemDictionaryItem> findByCode(String dictionaryId, String itemCode) {
            return values.values().stream().filter(value -> value.dictionaryId().equals(dictionaryId)
                    && value.itemCode().equals(itemCode)).findFirst();
        }

        @Override
        public SystemDictionaryItem insert(SystemDictionaryItem item) {
            if (findByCode(item.dictionaryId(), item.itemCode()).isPresent()) {
                throw new SystemDictionaryException.Conflict("duplicate");
            }
            String id = "i" + ++sequence;
            Instant now = Instant.parse("2026-07-30T00:00:00Z");
            SystemDictionaryItem persisted = new SystemDictionaryItem(id, item.dictionaryId(), item.itemCode(),
                    item.itemLabel(), item.sortOrder(), item.enabled(), 0L, item.description(),
                    "s14-test-actor", now, "s14-test-actor", now);
            values.put(id, persisted);
            return persisted;
        }

        @Override
        public boolean update(SystemDictionaryItem item) {
            SystemDictionaryItem current = values.get(item.id());
            if (current == null || current.version() != item.version()) {
                return false;
            }
            values.put(current.id(), persisted(current, item.itemLabel(), item.sortOrder(), current.enabled(),
                    item.description()));
            return true;
        }

        @Override
        public boolean updateStatus(SystemDictionaryItem item) {
            SystemDictionaryItem current = values.get(item.id());
            if (current == null || current.version() != item.version()) {
                return false;
            }
            values.put(current.id(), persisted(current, current.itemLabel(), current.sortOrder(), item.enabled(),
                    current.description()));
            return true;
        }

        @Override
        public List<SystemDictionaryItem> findActive(String dictionaryId) {
            return sorted(values.values().stream().filter(value -> value.dictionaryId().equals(dictionaryId)
                    && value.enabled()).toList());
        }

        @Override
        public ItemPage findPage(ItemQuery query) {
            List<SystemDictionaryItem> filtered = sorted(values.values().stream()
                    .filter(value -> value.dictionaryId().equals(query.dictionaryId()))
                    .filter(value -> query.itemCode() == null || value.itemCode().equals(query.itemCode()))
                    .filter(value -> query.enabled() == null || value.enabled() == query.enabled()).toList());
            int from = Math.min(query.page() * query.size(), filtered.size());
            int to = Math.min(from + query.size(), filtered.size());
            return new ItemPage(filtered.subList(from, to), filtered.size(), query.page(), query.size());
        }

        private static List<SystemDictionaryItem> sorted(List<SystemDictionaryItem> source) {
            List<SystemDictionaryItem> sorted = new ArrayList<>(source);
            sorted.sort(Comparator.comparingInt(SystemDictionaryItem::sortOrder)
                    .thenComparing(SystemDictionaryItem::itemCode).thenComparing(SystemDictionaryItem::id));
            return List.copyOf(sorted);
        }

        private static SystemDictionaryItem persisted(
                SystemDictionaryItem current, String label, int order, boolean enabled, String description) {
            return new SystemDictionaryItem(current.id(), current.dictionaryId(), current.itemCode(), label, order,
                    enabled, current.version() + 1, description, current.createdBy(), current.createdAt(),
                    "s14-test-actor", current.updatedAt().plusSeconds(1));
        }
    }
}
