package io.github.chrisshi.mom.coderule.store;

import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 保护移除 MyBatis-Plus 后的分页上限、元数据、越界及溢出语义。 */
class JdbcCodeRuleStorePaginationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private JdbcCodeRuleStore store(long maxPageSize) {
        var store = new JdbcCodeRuleStore(mock(DataSource.class), JsonMapper.builder().build(),
                maxPageSize, Optional::empty, Clock.systemUTC(), "mdm");
        ReflectionTestUtils.setField(store, "jdbc", jdbc);
        return store;
    }

    @Test void invalidLimitAndOffsetFailBeforeSql() {
        assertThatThrownBy(() -> store(0)).isInstanceOf(IllegalArgumentException.class);
        var store = store(50);
        assertThatThrownBy(() -> store.search(query(1, 51)))
                .isInstanceOf(PageQueryValidationException.class).hasMessageContaining("50");
        assertThatThrownBy(() -> store.search(query(Long.MAX_VALUE, 10)))
                .isInstanceOfSatisfying(CodeRuleFailure.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(CodeRuleFailure.Kind.BAD_REQUEST);
                    assertThat(failure.code()).isEqualTo("page_no_invalid");
                });
        verifyNoInteractions(jdbc);
    }

    @Test void totalsUseCeilingWithoutOverflowAndKeepRequestedPage() {
        var store = store(50);
        for (long total : new long[]{0, 1, 20, 21, Long.MAX_VALUE}) {
            when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(total);
            when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
            var result = store.search(query(4, 10));
            assertThat(result.pageNo()).isEqualTo(4);
            assertThat(result.pageSize()).isEqualTo(10);
            assertThat(result.total()).isEqualTo(total);
            assertThat(result.totalPages()).isEqualTo(total / 10 + (total % 10 == 0 ? 0 : 1));
            assertThat(result.records()).isEmpty();
        }
        verify(jdbc, times(5)).query(contains("ORDER BY r.rule_code ASC,v.revision_no DESC,v.id ASC LIMIT ? OFFSET ?"),
                any(RowMapper.class), eq(10L), eq(30L));
    }

    @Test void largestNonOverflowingOffsetRemainsValid() {
        var store = store(50);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThat(store.search(query(Long.MAX_VALUE, 1)).pageNo()).isEqualTo(Long.MAX_VALUE);
        verify(jdbc).query(anyString(), any(RowMapper.class), eq(1L), eq(Long.MAX_VALUE - 1));
    }

    private static PageQuery<CodeRulePageParams> query(long pageNo, long pageSize) {
        return new PageQuery<>(new CodeRulePageParams(null, null), pageNo, pageSize);
    }
}
