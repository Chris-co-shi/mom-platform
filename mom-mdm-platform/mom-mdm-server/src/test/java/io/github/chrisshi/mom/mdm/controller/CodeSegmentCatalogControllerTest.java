package io.github.chrisshi.mom.mdm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentRegistry;
import io.github.chrisshi.mom.mdm.codesegment.MdmCodeSegmentHandlers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CodeSegmentCatalogControllerTest {
    @Test void catalogMatchesFrontendV1ShapeAndContainsEveryDateOption() throws Exception {
        var config = new MdmCodeSegmentHandlers();
        var registry = new CodeSegmentRegistry(List.of(config.literalCodeSegment(), config.dateCodeSegment(), config.sequenceCodeSegment()));
        var mvc = MockMvcBuilders.standaloneSetup(new CodeSegmentCatalogController(registry)).build();
        mvc.perform(get("/code-rules/segments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[?(@.type=='DATE')].parameterSchema.schemaVersion").value(1))
                .andExpect(jsonPath("$.data[?(@.type=='DATE')].parameterSchema.fields[0].options.length()").value(4))
                .andExpect(jsonPath("$.data[?(@.type=='SEQUENCE')].parameterSchema.fields[0].integer").value(true))
                .andExpect(jsonPath("$..className").doesNotExist());
    }
}
