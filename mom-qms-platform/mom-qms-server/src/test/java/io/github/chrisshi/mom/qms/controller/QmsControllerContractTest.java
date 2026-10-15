package io.github.chrisshi.mom.qms.controller;

import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.qms.application.QmsException;
import io.github.chrisshi.mom.qms.application.QualityDefinitionApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** QMS Controller 的路由、Decimal String 和 Result 错误契约快速测试。 */
class QmsControllerContractTest {
    private QualityDefinitionApplication application;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        application = mock(QualityDefinitionApplication.class);
        mvc = standaloneSetup(new InspectionCharacteristicController(application),
                new InspectionMethodController(application), new QualitySpecificationController(application))
                .setControllerAdvice(new QmsExceptionHandler((key, locale, args) -> key)).build();
    }
    @Test void searchMustUsePostAndNeverExposeApiPrefix() throws Exception {
        when(application.characteristics(any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0, 0));
        mvc.perform(post("/inspection-characteristics/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{\"enabled\":true},\"pageNo\":1,\"pageSize\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/inspection-characteristics/search")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/inspection-characteristics/search")).andExpect(status().isNotFound());
    }
    @Test void numericJsonMustBeRejectedBeforeApplication() throws Exception {
        mvc.perform(post("/quality-specifications").contentType(MediaType.APPLICATION_JSON).content("""
          {"code":"DMC-IN","name":"DMC","plantId":"1","materialId":"2","items":[
            {"sequenceNo":10,"characteristicId":"3","methodId":"4","uomId":"5",
             "lowerLimit":99.9,"targetValue":"99.95","upperLimit":"100","mandatory":true}]}
          """))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("qms.invalid_range"));
        verify(application, never()).createSpecification(any(), any(), any(), any(), any());
    }
    @Test void applicationConflictMustKeep409AndStableCode() throws Exception {
        when(application.updateMethod(any(), any(), any(), any(), any())).thenThrow(QmsException.versionConflict());
        mvc.perform(put("/inspection-methods/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"GC\",\"description\":null,\"enabled\":true,\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("qms.version_conflict"));
    }
    @Test void nullPageParamsMustReturnStable400() throws Exception {
        mvc.perform(post("/inspection-methods/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":null,\"pageNo\":1,\"pageSize\":20}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.pagination_invalid"));
    }
}
