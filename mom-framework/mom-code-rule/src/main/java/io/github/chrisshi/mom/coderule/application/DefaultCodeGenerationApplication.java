package io.github.chrisshi.mom.coderule.application;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.field.CodeGenerationContext;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRenderContext;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * 使用 Owner 本地事务、规则行锁和 PostgreSQL 原子流水生成正式编码。
 */
@RequiredArgsConstructor
public class DefaultCodeGenerationApplication implements CodeGenerationApplication {
    private static final String FORMAT = "[\\p{L}\\p{N}._-]{1,128}";
    private final CodeRuleStore store;
    private final CodeSegmentRegistry handlers;
    private final CodeFieldRegistry fields;
    private final Clock clock;

    /**
     * 发号与幂等记录同事务提交；已有结果在规则停用或改版后仍可按原请求键重取。
     */
    @Override
    @Transactional
    public CodeGenerationResult generate(String ruleCode, Map<String, String> context, String requestKey) {
        if (ruleCode == null || !ruleCode.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) bad("rule_code_invalid");
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{1,128}")) bad("request_key_invalid");
        if (context == null || context.size() > 32 || context.entrySet().stream().anyMatch(e ->
            e.getKey() == null || !e.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                || e.getValue() == null || e.getValue().length() > 256)) bad("context_invalid");
        String hash = hash(context);
        CodeRuleStore.Rule rule = store.lockRuleByCode(ruleCode);
        if (rule == null) throw new CodeRuleFailure(CodeRuleFailure.Kind.NOT_FOUND, "rule_not_found");
        CodeRuleStore.Issue existing = store.issued(requestKey);
        if (existing != null) {
            if (!existing.ruleId().equals(rule.id()) || !existing.requestHash().equals(hash))
                conflict("request_key_conflict");
            return new CodeGenerationResult(ruleCode, existing.revisionId(), existing.revisionNo(), existing.code());
        }
        CodeRuleStore.Revision revision = store.revisions(rule.id()).stream()
            .filter(r -> "ENABLED".equals(r.status())).findFirst()
            .orElseThrow(() -> new CodeRuleFailure(CodeRuleFailure.Kind.CONFLICT, "rule_not_enabled"));
        CodeRuleDefinition definition = revision.definition();
        if (definition == null || definition.schemaVersion() != 1 || definition.segments() == null || definition.segments().isEmpty())
            conflict("definition_invalid");
        var frozenClock = Clock.fixed(clock.instant(), ZoneOffset.UTC);
        var renderContext = new CodeSegmentRenderContext(frozenClock, new CodeGenerationContext(context));
        StringBuilder before = new StringBuilder();
        CodeRuleDefinition.Segment sequence = null;
        int sequencePosition = -1;
        try {
            for (var segment : definition.segments()) {
                if (segment == null || segment.params() == null) conflict("definition_invalid");
                var handler = handlers.require(segment.type());
                handler.validate(segment.params());
                if ("SEQUENCE".equals(segment.type())) {
                    if (sequence != null) conflict("sequence_count_invalid");
                    sequence = segment;
                    sequencePosition = before.length();
                } else {
                    if ("BUSINESS_FIELD".equals(segment.type())) {
                        String fieldKey = (String) segment.params().get("fieldKey");
                        for (String key : fields.require(fieldKey).metadata().requiredContextKeys()) {
                            if (!context.containsKey(key) || context.get(key).isBlank()) bad("context_missing");
                        }
                    }
                    if (handler.metadata().capability() != io.github.chrisshi.mom.coderule.segment.CodeSegmentMetadata.Capability.STATELESS_RENDERABLE)
                        conflict("segment_unsupported");
                    String value = handlers.render(segment.type(), segment.params(), renderContext);
                    if (value == null || value.isEmpty() || !value.matches(FORMAT)) bad("segment_value_invalid");
                    before.append(value);
                }
            }
            if (sequence == null) conflict("sequence_count_invalid");
            String reset = reset(sequence.params());
            if (!reset.equals("NONE") && !hasMatchingDate(definition, reset)) conflict("reset_date_missing");
            int width = ((Number) sequence.params().get("width")).intValue();
            long maximum = powerOfTen(width) - 1;
            String scope = scope(reset, LocalDate.now(frozenClock));
            Long next = store.nextSequence(rule.id(), scope, maximum);
            if (next == null) conflict("sequence_overflow");
            String serial = Long.toString(next);
            String code = before.insert(sequencePosition, "0".repeat(width - serial.length()) + serial).toString();
            if (!code.matches(FORMAT)) bad("code_invalid");
            var issue = new CodeRuleStore.Issue(rule.id(), requestKey, hash, code, revision.id(), revision.revisionNo());
            try {
                if (!store.insertIssue(issue)) conflict("request_key_conflict");
            } catch (DuplicateKeyException ex) {
                conflict("code_collision");
            }
            return new CodeGenerationResult(ruleCode, revision.id(), revision.revisionNo(), code);
        } catch (CodeSegmentException | UnsupportedOperationException | ClassCastException ex) {
            bad("segment_execution_failed");
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 重置只允许和包含相应 UTC 周期的完整年份日期片段一起发布。
     */
    public static boolean hasMatchingDate(CodeRuleDefinition definition, String reset) {
        for (var segment : definition.segments()) {
            if ("DATE".equals(segment.type()) && segment.params().get("format") instanceof String format) {
                if ("DAY".equals(reset) && "YYYYMMDD".equals(format)
                    || ("YEAR".equals(reset) || "MONTH".equals(reset)) && ("YYYYMM".equals(format) || "YYYYMMDD".equals(format)))
                    return true;
            }
        }
        return "NONE".equals(reset);
    }

    private static String reset(Map<String, Object> params) {
        Object value = params.getOrDefault("reset", "NONE");
        if (!(value instanceof String text) || !java.util.List.of("NONE", "YEAR", "MONTH", "DAY").contains(text))
            bad("reset_invalid");
        return (String) value;
    }

    private static String scope(String reset, LocalDate date) {
        return switch (reset) {
            case "YEAR" -> "%04d".formatted(date.getYear());
            case "MONTH" -> "%04d%02d".formatted(date.getYear(), date.getMonthValue());
            case "DAY" -> "%04d%02d%02d".formatted(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            default -> "";
        };
    }

    private static long powerOfTen(int width) {
        long value = 1;
        for (int i = 0; i < width; i++) value *= 10;
        return value;
    }

    private static String hash(Map<String, String> context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            new TreeMap<>(context).forEach((key, value) -> {
                add(digest, key);
                add(digest, value);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void bad(String code) {
        throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, code);
    }

    private static void conflict(String code) {
        throw new CodeRuleFailure(CodeRuleFailure.Kind.CONFLICT, code);
    }
}
