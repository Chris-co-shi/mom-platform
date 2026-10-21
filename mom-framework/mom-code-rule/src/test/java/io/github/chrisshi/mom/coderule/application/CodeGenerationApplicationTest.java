package io.github.chrisshi.mom.coderule.application;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.handler.DateCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.handler.LiteralCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.handler.SequenceCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CodeGenerationApplicationTest {
    @Test void rendersPublishedSnapshotAndReturnsSameCodeForRetry() {
        CodeRuleStore store = mock(CodeRuleStore.class);
        var definition = new CodeRuleDefinition(1, List.of(
                new CodeRuleDefinition.Segment("p", "LITERAL", Map.of("value", "M-")),
                new CodeRuleDefinition.Segment("d", "DATE", Map.of("format", "YYYYMMDD")),
                new CodeRuleDefinition.Segment("s", "SEQUENCE", Map.of("width", 4, "step", 1))));
        when(store.lockRuleByCode("part")).thenReturn(new CodeRuleStore.Rule("123", "part", "测试", null, 1));
        when(store.revisions("123")).thenReturn(List.of(new CodeRuleStore.Revision("456", "123", 2, "ENABLED", definition,
                Instant.EPOCH, Instant.EPOCH)));
        when(store.nextSequence("123", "", 9999)).thenReturn(1L);
        when(store.insertIssue(any())).thenReturn(true);
        var app = new DefaultCodeGenerationApplication(store,
                new CodeSegmentRegistry(List.of(new LiteralCodeSegmentHandler(), new DateCodeSegmentHandler(), new SequenceCodeSegmentHandler())),
                new CodeFieldRegistry(List.of()), Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC));

        var first = app.generate("part", Map.of(), "req-1");
        assertThat(first.code()).isEqualTo("M-202603040001");
        assertThat(first.revisionNo()).isEqualTo(2);
        var issue = org.mockito.ArgumentCaptor.forClass(CodeRuleStore.Issue.class);
        verify(store).insertIssue(issue.capture());
        when(store.issued("req-1")).thenReturn(issue.getValue());
        assertThat(app.generate("part", Map.of(), "req-1").code()).isEqualTo(first.code());
        verify(store, times(1)).nextSequence("123", "", 9999);
    }
}
