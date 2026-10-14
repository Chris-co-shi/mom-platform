package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.i18n.management.I18nManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证管理列表按当前 Owner 查询且不暴露 namespace 参数，不启动宿主和数据库。
 * 宿主鉴权仍由独立安全测试负责。
 */
class I18nManagementControllerTest {
    @Test
    void ownerWideQueryDoesNotExposeNamespaceFilter() throws Exception {
        var service = mock(I18nManagementService.class);
        when(service.messages()).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new I18nManagementController(service))
                .addPlaceholderValue("mom.i18n.management.base-path", "/i18n/messages").build();
        mvc.perform(get("/i18n/messages")).andExpect(status().isOk());
        verify(service, times(1)).messages();
    }
}
