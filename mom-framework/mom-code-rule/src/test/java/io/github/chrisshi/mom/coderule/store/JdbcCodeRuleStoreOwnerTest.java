package io.github.chrisshi.mom.coderule.store;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Clock;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Store 构造边界拒绝非标识符 Owner，不允许请求提供 Schema 或任意表名。 */
class JdbcCodeRuleStoreOwnerTest {
    @Test void fixedTableNamesRespectPostgresqlIdentifierLimitWithoutOpeningConnection() {
        var source = mock(DataSource.class);
        for (String owner : new String[]{"mdm", "test_owner", "a".repeat(44)}) {
            var store = new JdbcCodeRuleStore(source, JsonMapper.builder().build(), 200, Optional::empty, Clock.systemUTC(), owner);
            assertThat(ReflectionTestUtils.getField(store, "ruleTable")).isEqualTo(owner + "_code_rule");
            assertThat(ReflectionTestUtils.getField(store, "revisionTable")).isEqualTo(owner + "_code_rule_revision");
        }
        for (String owner : new String[]{"", "a".repeat(45), "MDM", "mdm.other", "\"mdm\"", "mdm;SELECT 1", "mdm/*", " mdm", "mdm\n"}) {
            assertThatThrownBy(() -> new JdbcCodeRuleStore(source, JsonMapper.builder().build(), 200, Optional::empty, Clock.systemUTC(), owner))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(source);
    }
}
