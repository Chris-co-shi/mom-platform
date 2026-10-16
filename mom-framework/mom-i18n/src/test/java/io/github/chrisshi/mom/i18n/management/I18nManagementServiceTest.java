package io.github.chrisshi.mom.i18n.management;

import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.runtime.I18nChangePublisher;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 验证 Framework 管理用例的 Key 规范化、版本和译文约束，不启动数据库。
 * Store 由宿主提供；本测试只断言管理层在写入前后的行为，提交后通知由独立测试覆盖。
 */
class I18nManagementServiceTest {
    private I18nStore store;
    private I18nChangePublisher changes;
    private I18nManagementService service;

    @BeforeEach
    void setUp() {
        store = mock(I18nStore.class);
        changes = mock(I18nChangePublisher.class);
        service = new I18nManagementService(store, new LocalBaseLocalePolicy("zh-CN"), changes);
    }

    @Test
    void ownerStoreReturnsAllMessagesWithoutSubgroups() {
        I18nMessage first = message(3, true);
        I18nMessage second = new I18nMessage("1002", "mdm.uom.title", null, false, 1, Instant.EPOCH);
        when(store.messages()).thenReturn(List.of(first, second));

        assertThat(service.messages()).containsExactly(first, second);
        verifyNoInteractions(changes);
    }

    @Test
    void invalidKeyMustFailBeforeStoreAccess() {
        assertThatThrownBy(() -> service.createMessage("has space", null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createMessage("全角key", null, true))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store, changes);
    }

    @Test
    void serviceWideReadFailureMustNotBecomeEmptySuccess() {
        when(store.messages()).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(service::messages)
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
    }

    @Test
    void uniqueConflictMustUseStableFailureCode() {
        when(store.createMessage("mdm.material.title", null, true))
                .thenThrow(new DuplicateKeyException("unique constraint"));

        assertFailure(() -> service.createMessage("MDM.Material.Title", null, true),
                "i18n.message_key_conflict");
        verifyNoInteractions(changes);
    }

    @Test
    void nonUniqueConstraintFailureMustNotBecomeKeyConflict() {
        var failure = new DataIntegrityViolationException("check or not-null constraint");
        when(store.createMessage("title", null, true)).thenThrow(failure);

        assertThatThrownBy(() -> service.createMessage("title", null, true)).isSameAs(failure);
        verifyNoInteractions(changes);
    }

    @Test
    void duplicateTranslationLookupMustOnlyWarnAndMustNotBlockSaving() {
        when(store.message("1001", false)).thenReturn(message(3, true));
        when(store.duplicateMessageKeys("1001", "en-US", "Same text"))
                .thenReturn(List.of("mdm.material.name"));
        assertThat(service.duplicateMessageKeys("1001", "en-US", " Same text "))
                .containsExactly("mdm.material.name");

        when(store.message("1001", true)).thenReturn(message(3, true));
        when(store.translations("1001")).thenReturn(List.of());
        when(store.createTranslation("1001", "en-US", "Same text"))
                .thenReturn(translation("en-US", "Same text", 0));
        assertThat(service.saveTranslation("1001", "en-US", "Same text", null).messageText())
                .isEqualTo("Same text");
        verify(store).createTranslation("1001", "en-US", "Same text");
    }

    @Test
    void staleMessageVersionMustNotWriteOrNotify() {
        when(store.message("1001", false)).thenReturn(message(3, true));

        assertFailure(() -> service.updateMessage("1001", null, false, 2L), "i18n.stale_version");
        verify(store, never()).updateMessage(anyString(), any(), anyBoolean(), anyLong());
        verifyNoInteractions(changes);
    }

    @Test
    void placeholderMismatchMustFailBeforeTranslationWrite() {
        when(store.message("1001", true)).thenReturn(message(3, true));
        when(store.translations("1001")).thenReturn(List.of(translation("zh-CN", "你好 {0}", 3)));

        assertFailure(() -> service.saveTranslation("1001", "en-US", "Hello {1}", null),
                "i18n.placeholder_mismatch");
        verify(store, never()).createTranslation(anyString(), anyString(), anyString());
        verifyNoInteractions(changes);
    }

    @Test
    void staleTranslationVersionMustNotWriteOrNotify() {
        when(store.message("1001", true)).thenReturn(message(3, true));
        when(store.translations("1001")).thenReturn(List.of(translation("en-US", "Hello {0}", 3)));

        assertFailure(() -> service.saveTranslation("1001", "en-US", "Hi {0}", 2L),
                "i18n.stale_version");
        verify(store, never()).updateTranslation(anyString(), anyString(), anyLong());
        verifyNoInteractions(changes);
    }

    @Test
    void enabledChangeAndTranslationSaveMustPublishLocaleInvalidation() {
        when(store.message("1001", false)).thenReturn(message(3, true), message(4, false));
        when(store.updateMessage("1001", null, false, 3)).thenReturn(true);
        service.updateMessage("1001", null, false, 3L);
        verify(changes).bundleChanged("*");

        when(store.message("1001", true)).thenReturn(message(4, false));
        when(store.translations("1001")).thenReturn(List.of());
        when(store.createTranslation("1001", "en-US", "Hello {0}"))
                .thenReturn(translation("en-US", "Hello {0}", 0));
        service.saveTranslation("1001", "en-US", "Hello {0}", null);
        verify(changes).bundleChanged("en-US");
    }

    private void assertFailure(Runnable operation, String code) {
        assertThatThrownBy(operation::run).isInstanceOf(I18nFailure.class)
                .satisfies(error -> assertThat(((I18nFailure) error).code()).isEqualTo(code));
    }

    private I18nMessage message(long version, boolean enabled) {
        return new I18nMessage("1001", "mdm.material.title", null, enabled, version, Instant.EPOCH);
    }

    private I18nTranslation translation(String locale, String text, long version) {
        return new I18nTranslation("2001", "1001", locale, text, version, Instant.EPOCH);
    }
}
