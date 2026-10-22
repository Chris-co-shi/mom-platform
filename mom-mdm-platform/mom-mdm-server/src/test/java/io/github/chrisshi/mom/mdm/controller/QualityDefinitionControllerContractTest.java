package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.MdmException;
import io.github.chrisshi.mom.mdm.application.QualityDefinitionApplication;
import io.github.chrisshi.mom.mdm.api.QualitySpecificationReference;
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

/** MDM 质量定义 Controller 的路由、Decimal String 和 Result 错误契约快速测试。 */
class QualityDefinitionControllerContractTest {
    private QualityDefinitionApplication application;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        application = mock(QualityDefinitionApplication.class);
        mvc = standaloneSetup(new InspectionCharacteristicController(application),
                new InspectionMethodController(application), new QualitySpecificationController(application),
                new QualitySpecificationReferenceController(application))
                .setControllerAdvice(new MdmExceptionHandler((key, locale, args) -> key)).build();
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
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("mdm.quality_invalid_range"));
        verify(application, never()).createSpecification(any(), any(), any(), any(), any(), any());
    }
    @Test void finishedGoodsUsageMustReachMdmApplication() throws Exception {
        mvc.perform(post("/quality-specifications").contentType(MediaType.APPLICATION_JSON).content("""
          {"code":"FG-1","name":"成品标准","plantId":"1","materialId":"2",
           "inspectionUsage":"FINISHED_GOODS","items":[
            {"sequenceNo":10,"characteristicId":"3","methodId":"4","uomId":"5",
             "lowerLimit":"99","targetValue":"99.5","upperLimit":"100","mandatory":true}]}
          """))
          .andExpect(status().isCreated());
        verify(application).createSpecification(eq("FG-1"), eq("成品标准"), eq("1"), eq("2"),
                eq("FINISHED_GOODS"), any());
    }
    @Test void applicationConflictMustKeep409AndStableCode() throws Exception {
        when(application.updateMethod(any(), any(), any(), any(), any())).thenThrow(MdmException.versionConflict());
        mvc.perform(put("/inspection-methods/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"GC\",\"description\":null,\"enabled\":true,\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("mdm.version_conflict"));
    }
    @Test void nullPageParamsMustReturnStable400() throws Exception {
        mvc.perform(post("/inspection-methods/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":null,\"pageNo\":1,\"pageSize\":20}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.pagination_invalid"));
    }

    @Test void publishedReferenceMustUseStableMdmEndpoint() throws Exception {
        when(application.reference("100")).thenReturn(new QualitySpecificationReference(
                "100", "DMC-IN", "DMC 来料标准", "1", "2", "INCOMING",
                "101", 1, null, List.of(), true, null));
        mvc.perform(get("/quality-specification-references/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DMC-IN"))
                .andExpect(jsonPath("$.data.availableForNewUse").value(true));
    }
}
