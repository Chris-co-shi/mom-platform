package io.github.chrisshi.mom.system.domain.dictionary;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** System 非权威字典 Code、Label、排序、不可变 Reference 与启停兼容语义单元测试。 */
class SystemDictionaryRulesTest {

    @Test
    void dictionaryCodeMustNormalizeLowercaseAndRequireNamespace() {
        assertThat(SystemDictionaryRules.normalizeDictionaryCode(" System.Common-View "))
                .isEqualTo("system.common-view");
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeDictionaryCode("single"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeDictionaryCode("system..view"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeDictionaryCode("system.common_view"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dictionaryNameMustBeFallbackTextWithoutControlCharacters() {
        assertThat(SystemDictionaryRules.normalizeDictionaryName("  Common View  "))
                .isEqualTo("Common View");
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeDictionaryName(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeDictionaryName("bad\nname"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemCodeMustNormalizeLowercaseAndRejectDotsOrLeadingDigits() {
        assertThat(SystemDictionaryRules.normalizeItemCode(" Ready_State-1 "))
                .isEqualTo("ready_state-1");
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeItemCode("1ready"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeItemCode("ready.state"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemLabelMustRejectBlankControlAndOversizedValues() {
        assertThat(SystemDictionaryRules.normalizeItemLabel(" Ready for display "))
                .isEqualTo("Ready for display");
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeItemLabel("\t"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemDictionaryRules.normalizeItemLabel("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortOrderMustHonorBothBoundaries() {
        assertThat(SystemDictionaryRules.requireSortOrder(0)).isZero();
        assertThat(SystemDictionaryRules.requireSortOrder(SystemDictionaryRules.MAX_SORT_ORDER))
                .isEqualTo(SystemDictionaryRules.MAX_SORT_ORDER);
        assertThatThrownBy(() -> SystemDictionaryRules.requireSortOrder(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemDictionaryRules.requireSortOrder(
                SystemDictionaryRules.MAX_SORT_ORDER + 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stableCodesAndParentMustRemainImmutableWhileLabelsMayChange() {
        SystemDictionary dictionary = SystemDictionary.newDictionary(
                "system.common.state", "State", true, null);
        SystemDictionary changedDictionary = dictionary.update(0L, "Display State", "changed");
        assertThat(changedDictionary.dictionaryCode()).isEqualTo(dictionary.dictionaryCode());
        assertThat(changedDictionary.dictionaryName()).isEqualTo("Display State");

        SystemDictionaryItem item = SystemDictionaryItem.newItem(
                "dictionary-1", "ready", "Ready", 10, true, null);
        SystemDictionaryItem changedItem = item.update(0L, "Ready for display", 20, "changed");
        assertThat(changedItem.dictionaryId()).isEqualTo("dictionary-1");
        assertThat(changedItem.itemCode()).isEqualTo("ready");
        assertThat(changedItem.itemLabel()).isEqualTo("Ready for display");
    }

    @Test
    void effectiveEnabledMustCombineStatesWithoutMutatingItem() {
        SystemDictionary dictionary = SystemDictionary.newDictionary(
                "system.common.state", "State", true, null);
        SystemDictionaryItem item = SystemDictionaryItem.newItem(
                "dictionary-1", "ready", "Ready", 10, true, null);
        assertThat(item.effectiveEnabled(dictionary.enabled())).isTrue();

        SystemDictionary disabledDictionary = dictionary.changeStatus(0L, false);
        assertThat(item.effectiveEnabled(disabledDictionary.enabled())).isFalse();
        assertThat(item.enabled()).isTrue();

        SystemDictionaryItem disabledItem = item.changeStatus(0L, false);
        assertThat(disabledItem.effectiveEnabled(true)).isFalse();
    }

    @Test
    void domainModelsMustNotExposeMetadataTreeAliasOrLocale() {
        assertThat(Arrays.stream(SystemDictionary.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("id", "dictionaryCode", "dictionaryName", "enabled", "version", "description",
                        "createdBy", "createdAt", "updatedBy", "updatedAt");
        assertThat(Arrays.stream(SystemDictionaryItem.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("id", "dictionaryId", "itemCode", "itemLabel", "sortOrder", "enabled", "version",
                        "description", "createdBy", "createdAt", "updatedBy", "updatedAt")
                .doesNotContain("metadata", "parentId", "alias", "locale", "labelMap");
    }
}
