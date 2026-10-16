package io.github.chrisshi.mom.i18n.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 受信表名边界、空批量与连接故障行为。 */
class JdbcI18nStoreTest {
    private final DataSource dataSource = mock(DataSource.class);

    @Test
    void identifiersMustBeValidatedBeforeAnyConnection() {
        for (String name : List.of("messages; DROP TABLE messages", "messages --", "a.b.c", "\"messages\"", "a b", "a..b", "a\nb", "A", "a".repeat(64))) {
            assertThatIllegalArgumentException().isThrownBy(() -> store(name, "translations"));
            assertThatIllegalArgumentException().isThrownBy(() -> store("messages", name));
        }
        store("owner.messages", "owner.translations");
        verifyNoInteractions(dataSource);
    }

    @Test
    void emptyBatchesDoNotAccessDatabase() {
        var store = store("messages", "translations");
        assertThat(store.translations(List.of(), List.of("zh-CN"))).isEmpty();
        assertThat(store.translations(List.of("1"), List.of())).isEmpty();
        verifyNoInteractions(dataSource);
    }

    @Test
    void connectionFailureMustPropagate() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("unavailable", "08006"));
        var store = store("messages", "translations");
        assertThatThrownBy(store::enabledMessages).isInstanceOf(CannotGetJdbcConnectionException.class);
        assertThatThrownBy(() -> store.createMessage("test.key", null, true))
                .isInstanceOf(CannotGetJdbcConnectionException.class);
        assertThatThrownBy(() -> store.translations(List.of("1", "2"), List.of("zh-CN", "en-US")))
                .isInstanceOf(CannotGetJdbcConnectionException.class);
    }

    @Test
    void bulkReadUsesOneStatementAndBindsAllValues() throws Exception {
        var connection = mock(java.sql.Connection.class);
        var statement = mock(java.sql.PreparedStatement.class);
        var result = mock(java.sql.ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);
        var store = store("messages", "translations");
        var ids = java.util.stream.IntStream.range(0, 100).mapToObj(Integer::toString).toList();
        assertThat(store.translations(ids, List.of("zh-CN", "en-US"))).isEmpty();
        verify(connection, times(1)).prepareStatement(anyString());
        verify(statement).setString(1, "0");
        verify(statement).setString(100, "99");
        verify(statement).setString(101, "zh-CN");
        verify(statement).setString(102, "en-US");
    }

    private JdbcI18nStore store(String messages, String translations) {
        return new JdbcI18nStore(dataSource, messages, translations, () -> "1", Optional::empty, Clock.systemUTC());
    }
}
