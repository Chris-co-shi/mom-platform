package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.field.CodeGenerationContext;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRenderContext;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import io.github.chrisshi.mom.wms.infrastructure.mapper.OpeningDocumentNumberOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Map;

/** 将已发布的 WMS 规则渲染为期初单正式编号；流水与单据共享本地事务。 */
@Service
public class OpeningDocumentNumberService {
    private static final String RULE_CODE = "wms.opening.document";
    private final OpeningDocumentNumberOperations numbers;
    private final CodeRuleStore rules;
    private final CodeSegmentRegistry handlers;
    private final Clock clock;

    /** @param numbers WMS 本地规则锁及流水 @param rules 当前 WMS Owner 的规则存储 @param handlers 已注册片段 @param clock 编码日期使用的 UTC 时钟 */
    public OpeningDocumentNumberService(OpeningDocumentNumberOperations numbers, CodeRuleStore rules,
                                        CodeSegmentRegistry handlers, Clock clock) {
        this.numbers = numbers; this.rules = rules; this.handlers = handlers; this.clock = clock;
    }

    /** 在期初单创建事务内取正式编号；规则缺失、停用或输出无效时抛错并回滚。 */
    public String allocate() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("期初单发号必须处于 WMS 本地事务中");
        String ruleId = numbers.lockRuleId(RULE_CODE);
        if (ruleId == null) throw new IllegalStateException("期初单编码规则不存在");
        CodeRuleDefinition definition = rules.revisions(ruleId).stream()
                .filter(revision -> "ENABLED".equals(revision.status()))
                .findFirst().orElseThrow(() -> new IllegalStateException("期初单编码规则未发布"))
                .definition();
        if (definition.schemaVersion() != 1 || definition.segments() == null)
            throw new IllegalStateException("期初单编码规则定义无效");
        int sequences = 0;
        for (CodeRuleDefinition.Segment segment : definition.segments()) {
            handlers.require(segment.type()).validate(segment.params());
            if ("SEQUENCE".equals(segment.type())) sequences++;
        }
        if (sequences != 1) throw new IllegalStateException("期初单编码规则必须包含一个流水片段");
        Long serial = numbers.next(ruleId);
        if (serial == null) throw new IllegalStateException("期初单流水号分配失败");
        var context = new CodeSegmentRenderContext(clock, new CodeGenerationContext(Map.of()));
        StringBuilder code = new StringBuilder();
        for (CodeRuleDefinition.Segment segment : definition.segments()) {
            if ("SEQUENCE".equals(segment.type())) {
                int width = ((Number) segment.params().get("width")).intValue();
                String digits = Long.toString(serial);
                if (digits.length() > width) throw new IllegalStateException("期初单流水号超过规则宽度");
                code.append("0".repeat(width - digits.length())).append(digits);
            } else {
                code.append(handlers.require(segment.type()).render(segment.params(), context));
            }
        }
        if (code.isEmpty() || code.length() > 32) throw new IllegalStateException("期初单编码超过数据库字段长度");
        return code.toString();
    }
}
