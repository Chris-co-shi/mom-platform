package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.i18n.management.I18nManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证管理列表的可选 namespace HTTP 绑定，不启动宿主和数据库。
 * 只覆盖兼容查询契约；宿主鉴权仍由独立安全测试负责。
 */
class I18nManagementControllerTest {
    @Test
    void serviceWideQueryDoesNotRequireNamespaceAndExactQueryStillWorks() throws Exception {
        var service = mock(I18nManagementService.class);
        when(service.messages(null)).thenReturn(List.of());
        when(service.messages("mdm.material")).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new I18nManagementController(service))
                .addPlaceholderValue("mom.i18n.management.base-path", "/admin/i18n/messages").build();
        mvc.perform(get("/admin/i18n/messages")).andExpect(status().isOk());
        mvc.perform(get("/admin/i18n/messages").param("namespace", "mdm.material"))
                .andExpect(status().isOk());
        verify(service).messages(null);
        verify(service).messages("mdm.material");
    }
}
