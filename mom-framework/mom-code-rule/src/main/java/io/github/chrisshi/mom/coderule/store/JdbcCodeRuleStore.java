package io.github.chrisshi.mom.coderule.store;

import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleOwner;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC 编码规则存储，只接入当前 Owner 的本地表。
 */
public final class JdbcCodeRuleStore implements CodeRuleStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final long maxPageSize;
    private final CurrentActorProvider actors;
    private final Clock clock;
    private final String ruleTable;
    private final String revisionTable;
    private final String sequenceTable;
    private final String issueTable;
    private final boolean mysql;
    private final String definitionParameter;

    /** 创建 Owner 本地 Store；分页上限沿用 mom.data.pagination.max-page-size。 */
    public JdbcCodeRuleStore(DataSource source, JsonMapper json, long maxPageSize,
                             CurrentActorProvider actors, Clock clock, String owner) {
        this(source, json, maxPageSize, actors, clock, owner, false);
    }

    /** 创建 Owner 本地 Store，并按部署配置选择数据库语法。 */
    public JdbcCodeRuleStore(DataSource source, JsonMapper json, long maxPageSize,
                             CurrentActorProvider actors, Clock clock, String owner, boolean mysql) {
        String prefix = new CodeRuleOwner(owner).value();
        this.jdbc = new JdbcTemplate(source);
        this.json = json;
        if (maxPageSize <= 0) throw new IllegalArgumentException("最大单页条数必须大于 0");
        this.maxPageSize = maxPageSize;
        this.actors = actors;
        this.clock = clock;
        this.ruleTable = prefix + "_code_rule";
        this.revisionTable = prefix + "_code_rule_revision";
        this.sequenceTable = prefix + "_code_rule_sequence";
        this.issueTable = prefix + "_code_rule_issue";
        this.mysql = mysql;
        this.definitionParameter = mysql ? "CAST(? AS JSON)" : "?::jsonb";
    }

    public Rule lockRule(String id) {
        return one(jdbc.query("SELECT id, rule_code, usage_scenario, description, version FROM " + ruleTable + " WHERE id=? FOR UPDATE",
            (r, n) -> new Rule(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getLong(5)), id));
    }

    public Rule lockRuleByCode(String code) {
        return one(jdbc.query("SELECT id, rule_code, usage_scenario, description, version FROM " + ruleTable + " WHERE rule_code=? FOR UPDATE",
            (r, n) -> new Rule(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getLong(5)), code));
    }

    public Issue issued(String requestKey) {
        return one(jdbc.query("SELECT rule_id,request_key,request_hash,code,revision_id,revision_no FROM " + issueTable
                + " WHERE request_key=?", (r, n) -> new Issue(r.getString(1), r.getString(2),
                r.getString(3), r.getString(4), r.getString(5), r.getInt(6)), requestKey));
    }

    /** 在持有规则身份行锁的事务内分配流水，达到上限时返回空值。 */
    public Long nextSequence(String ruleId, String scopeKey, long maximum) {
        if (mysql) return nextMysqlSequence(ruleId, scopeKey, maximum);
        return one(jdbc.query("INSERT INTO " + sequenceTable + "(rule_id,scope_key,last_value) VALUES (?,?,1)"
                + " ON CONFLICT(rule_id,scope_key) DO UPDATE SET last_value=" + sequenceTable + ".last_value+1"
                + " WHERE " + sequenceTable + ".last_value < ? RETURNING last_value",
                (r, n) -> r.getLong(1), ruleId, scopeKey, maximum));
    }

    private Long nextMysqlSequence(String ruleId, String scopeKey, long maximum) {
        // Application 已锁定所属规则，即使计数行尚未建立也只有一个事务可以初始化。
        Long current = one(jdbc.query("SELECT `last_value` FROM " + sequenceTable
                + " WHERE rule_id=? AND scope_key=? FOR UPDATE", (r, n) -> r.getLong(1), ruleId, scopeKey));
        if (current == null) {
            requireOne(jdbc.update("INSERT INTO " + sequenceTable
                    + "(rule_id,scope_key,`last_value`) VALUES (?,?,1)", ruleId, scopeKey));
            return 1L;
        }
        if (current >= maximum) return null;
        requireOne(jdbc.update("UPDATE " + sequenceTable
                + " SET `last_value`=`last_value`+1 WHERE rule_id=? AND scope_key=?", ruleId, scopeKey));
        return current + 1;
    }

    /** 保存发号结果，仅把请求主键冲突报告为幂等冲突。 */
    public boolean insertIssue(Issue issue) {
        String sql = "INSERT INTO " + issueTable
                + "(rule_id,request_key,request_hash,code,revision_id,revision_no,created_at) VALUES (?,?,?,?,?,?,?)";
        if (!mysql) sql += " ON CONFLICT(request_key) DO NOTHING";
        try {
            return jdbc.update(sql, issue.ruleId(), issue.requestKey(), issue.requestHash(), issue.code(),
                    issue.revisionId(), issue.revisionNo(), Timestamp.from(clock.instant())) == 1;
        } catch (DuplicateKeyException ex) {
            if (mysql && ex.getMostSpecificCause() instanceof SQLException cause && cause.getErrorCode() == 1062
                    && (cause.getMessage().endsWith("'PRIMARY'")
                    || cause.getMessage().endsWith("'" + issueTable + ".PRIMARY'"))) {
                return false;
            }
            throw ex;
        }
    }

    public Rule rule(String id) {
        return one(jdbc.query("SELECT id, rule_code, usage_scenario, description, version FROM " + ruleTable + " WHERE id=?",
            (r, n) -> new Rule(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getLong(5)), id));
    }

    public Revision revision(String id) {
        return one(jdbc.query(revisionSelect() + " WHERE id=?", this::mapRevision, id));
    }

    public List<Revision> revisions(String ruleId) {
        return jdbc.query(revisionSelect() + " WHERE rule_id=? ORDER BY revision_no", this::mapRevision, ruleId);
    }

    /** 持久化规则身份和说明，并沿用当前操作者与时钟填充审计。 */
    public void insertRule(String id, String ruleCode, String usageScenario, String description) {
        String actor = actors.requireCurrentActor().actorId();
        Timestamp now = Timestamp.from(clock.instant());
        requireOne(jdbc.update("INSERT INTO " + ruleTable + " (id,rule_code,usage_scenario,description,version,created_at,created_by,updated_at,updated_by) VALUES (?,?,?,?,0,?,?,?,?)",
            id, ruleCode, usageScenario, description, now, actor, now, actor));
    }

    /** 仅更新规则说明；调用者在同一本地事务中持有规则行锁并推进规则版本。 */
    public boolean updateMetadata(String id, String usageScenario, String description) {
        return jdbc.update("UPDATE " + ruleTable + " SET usage_scenario=?,description=? WHERE id=?", usageScenario, description, id) == 1;
    }

    /** 以当前数据库的 JSON 类型保存规则修订定义及审计信息。 */
    public void insertRevision(String id, String ruleId, int revisionNo, String status, CodeRuleDefinition definition) {
        String actor = actors.requireCurrentActor().actorId();
        Timestamp now = Timestamp.from(clock.instant());
        requireOne(jdbc.update("INSERT INTO " + revisionTable
                + " (id,rule_id,revision_no,status,definition,created_at,created_by,updated_at,updated_by)"
                + " VALUES (?,?,?,?," + definitionParameter + ",?,?,?,?)", id, ruleId, revisionNo, status,
            json.writeValueAsString(definition), now, actor, now, actor));
    }

    /** 更新仍处于草稿状态的定义，已发布修订不受影响。 */
    public boolean updateDraft(String id, CodeRuleDefinition definition) {
        return jdbc.update("UPDATE " + revisionTable + " SET definition=" + definitionParameter
                + ", updated_at=?,updated_by=? WHERE id=? AND status='DRAFT'",
            json.writeValueAsString(definition), Timestamp.from(clock.instant()), actors.requireCurrentActor().actorId(), id) == 1;
    }

    public boolean changeStatus(String id, String from, String to) {
        return jdbc.update("UPDATE " + revisionTable + " SET status=?,updated_at=?,updated_by=? WHERE id=? AND status=?",
            to, Timestamp.from(clock.instant()), actors.requireCurrentActor().actorId(), id, from) == 1;
    }

    public boolean advanceVersion(String id, long expected) {
        return jdbc.update("UPDATE " + ruleTable + " SET version=version+1,updated_at=?,updated_by=? WHERE id=? AND version=?",
            Timestamp.from(clock.instant()), actors.requireCurrentActor().actorId(), id, expected) == 1;
    }

    public PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query) {
        if (query.pageSize() > maxPageSize) {
            throw new PageQueryValidationException("pageSize", "每页条数不能超过 " + maxPageSize);
        }
        long offset;
        try {
            offset = Math.multiplyExact(query.pageNo() - 1, query.pageSize());
        } catch (ArithmeticException ex) {
            throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, "page_no_invalid");
        }
        CodeRulePageParams filters = query.params();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (filters.ruleCode() != null && !filters.ruleCode().isBlank()) {
            where.append(" AND r.rule_code=?");
            args.add(filters.ruleCode());
        }
        if (filters.status() != null && !filters.status().isBlank()) {
            if (!List.of("DRAFT", "ENABLED", "DISABLED").contains(filters.status()))
                throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, "status_invalid");
            where.append(" AND v.status=?");
            args.add(filters.status());
        }
        String from = " FROM " + revisionTable + " v JOIN " + ruleTable + " r ON v.rule_id=r.id";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        long count = total == null ? 0 : total;
        List<Object> paged = new ArrayList<>(args);
        paged.add(query.pageSize());
        paged.add(offset);
        List<CodeRuleView> records = jdbc.query("SELECT v.id,v.rule_id,r.rule_code,r.usage_scenario,r.description,v.revision_no,v.status,v.definition,r.version AS rule_version,"
                + "v.created_at,v.updated_at" + from + where
                + " ORDER BY r.rule_code ASC,v.revision_no DESC,v.id ASC LIMIT ? OFFSET ?",
            (r, n) -> new CodeRuleView(r.getString("id"), r.getString("rule_id"), r.getString("rule_code"), r.getString("usage_scenario"), r.getString("description"),
                r.getInt("revision_no"), r.getString("status"), decode(r.getString("definition")),
                r.getLong("rule_version"), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant()), paged.toArray());
        // 与原 IPage.getPages() 一致，使用商和余数避免 total + pageSize 溢出。
        long totalPages = count / query.pageSize() + (count % query.pageSize() == 0 ? 0 : 1);
        return new PageResult<>(records, query.pageNo(), query.pageSize(), count, totalPages);
    }

    private String revisionSelect() {
        return "SELECT id,rule_id,revision_no,status,definition,created_at,updated_at FROM " + revisionTable;
    }

    private Revision mapRevision(ResultSet r, int row) throws SQLException {
        return new Revision(r.getString("id"), r.getString("rule_id"), r.getInt("revision_no"), r.getString("status"),
            decode(r.getString("definition")), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }

    private CodeRuleDefinition decode(String value) {
        return json.readValue(value, CodeRuleDefinition.class);
    }

    private static <T> T one(List<T> rows) {
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static void requireOne(int rows) {
        if (rows != 1) throw new IllegalStateException("编码规则写入行数异常");
    }
}
