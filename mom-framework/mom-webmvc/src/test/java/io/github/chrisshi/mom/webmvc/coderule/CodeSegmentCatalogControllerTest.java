package io.github.chrisshi.mom.webmvc.coderule;

import io.github.chrisshi.mom.coderule.handler.DateCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.handler.LiteralCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.handler.SequenceCodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.mock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CodeSegmentCatalogControllerTest {
    @Test
    void catalogMatchesFrontendV1ShapeAndContainsEveryDateOption() throws Exception {
        var registry = new CodeSegmentRegistry(List.of(new LiteralCodeSegmentHandler(),
                new DateCodeSegmentHandler(), new SequenceCodeSegmentHandler()));
        var mvc = MockMvcBuilders.standaloneSetup(new CodeRuleController(mock(CodeRuleApplication.class), registry)).build();
        mvc.perform(get("/code-rules/segments"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[?(@.type=='DATE')].parameterSchema.schemaVersion").value(1))
            .andExpect(jsonPath("$.data[?(@.type=='DATE')].parameterSchema.fields[0].options.length()").value(4))
            .andExpect(jsonPath("$.data[?(@.type=='SEQUENCE')].parameterSchema.fields[0].min").value(1))
            .andExpect(jsonPath("$.data[?(@.type=='SEQUENCE')].parameterSchema.fields[1].readonly").value(true))
            .andExpect(jsonPath("$..minimum").doesNotExist())
            .andExpect(jsonPath("$..className").doesNotExist());
    }
}
