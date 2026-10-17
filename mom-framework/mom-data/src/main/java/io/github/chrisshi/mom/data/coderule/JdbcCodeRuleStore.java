package io.github.chrisshi.mom.data.coderule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.coderule.*;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.page.PageAdapter;
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
 * JDBC JSONB 规则 Store，只接入当前 Owner 的本地表。
 */
public final class JdbcCodeRuleStore implements CodeRuleStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final PageAdapter pages;
    private final CurrentActorProvider actors;
    private final Clock clock;
    private final String ruleTable;
    private final String revisionTable;

    public JdbcCodeRuleStore(DataSource source, JsonMapper json, PageAdapter pages,
                             CurrentActorProvider actors, Clock clock, String prefix) {
        if (!"mdm".equals(prefix)) throw new IllegalArgumentException("不支持的编码规则 Owner");
        this.jdbc = new JdbcTemplate(source);
        this.json = json;
        this.pages = pages;
        this.actors = actors;
        this.clock = clock;
        this.ruleTable = prefix + "_code_rule";
        this.revisionTable = prefix + "_code_rule_revision";
    }

    public Rule lockRule(String id) {
        return one(jdbc.query("SELECT id, rule_code, version FROM " + ruleTable + " WHERE id=? FOR UPDATE",
            (r, n) -> new Rule(r.getString(1), r.getString(2), r.getLong(3)), id));
    }

    public Rule rule(String id) {
        return one(jdbc.query("SELECT id, rule_code, version FROM " + ruleTable + " WHERE id=?",
            (r, n) -> new Rule(r.getString(1), r.getString(2), r.getLong(3)), id));
    }

    public Revision revision(String id) {
        return one(jdbc.query(revisionSelect() + " WHERE id=?", this::mapRevision, id));
    }

    public List<Revision> revisions(String ruleId) {
        return jdbc.query(revisionSelect() + " WHERE rule_id=? ORDER BY revision_no", this::mapRevision, ruleId);
    }

    public void insertRule(String id, String ruleCode) {
        String actor = actors.requireCurrentActor().actorId();
        Timestamp now = Timestamp.from(clock.instant());
        requireOne(jdbc.update("INSERT INTO " + ruleTable + " (id,rule_code,version,created_at,created_by,updated_at,updated_by) VALUES (?,?,0,?,?,?,?)",
            id, ruleCode, now, actor, now, actor));
    }

    public void insertRevision(String id, String ruleId, int revisionNo, String status, CodeRuleDefinition definition) {
        String actor = actors.requireCurrentActor().actorId();
        Timestamp now = Timestamp.from(clock.instant());
        requireOne(jdbc.update("INSERT INTO " + revisionTable
                + " (id,rule_id,revision_no,status,definition,created_at,created_by,updated_at,updated_by)"
                + " VALUES (?,?,?,?,?::jsonb,?,?,?,?)", id, ruleId, revisionNo, status,
            json.writeValueAsString(definition), now, actor, now, actor));
    }

    public boolean updateDraft(String id, CodeRuleDefinition definition) {
        return jdbc.update("UPDATE " + revisionTable + " SET definition=?::jsonb, updated_at=?,updated_by=? WHERE id=? AND status='DRAFT'",
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
        Page<CodeRuleView> page = pages.toPage(query);
        long offset;
        try {
            offset = Math.multiplyExact(page.getCurrent() - 1, page.getSize());
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
        page.setTotal(total == null ? 0 : total);
        List<Object> paged = new ArrayList<>(args);
        paged.add(page.getSize());
        paged.add(offset);
        page.setRecords(jdbc.query("SELECT v.id,v.rule_id,r.rule_code,v.revision_no,v.status,v.definition,r.version AS rule_version,"
                + "v.created_at,v.updated_at" + from + where
                + " ORDER BY r.rule_code ASC,v.revision_no DESC,v.id ASC LIMIT ? OFFSET ?",
            (r, n) -> new CodeRuleView(r.getString("id"), r.getString("rule_id"), r.getString("rule_code"),
                r.getInt("revision_no"), r.getString("status"), decode(r.getString("definition")),
                r.getLong("rule_version"), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant()), paged.toArray()));
        return pages.toResult(page);
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
