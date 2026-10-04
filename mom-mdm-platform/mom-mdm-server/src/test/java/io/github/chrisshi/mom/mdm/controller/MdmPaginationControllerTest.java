package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.UomPageParams;
import io.github.chrisshi.mom.mdm.application.UomMasterDataApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * MDM POST 分页协议的 Controller 快速测试。
 *
 * <p>测试使用独立 MockMvc 和模拟 Application，只验证 HTTP JSON 绑定、旧 GET 删除及分页异常适配，
 * 不连接数据库、不验证权限引擎或持久化查询；真实过滤和 PageResult 由 PostgreSQL IT 覆盖。</p>
 */
class MdmPaginationControllerTest {
    private UomMasterDataApplication application;
    private MockMvc mockMvc;

    /** 为每个测试创建无共享状态的 Controller 与异常适配器。 */
    @BeforeEach
    void setUp() {
        application = mock(UomMasterDataApplication.class);
        mockMvc = standaloneSetup(new UomMasterDataController(application))
                .setControllerAdvice(new MdmExceptionHandler())
                .build();
    }

    /** POST JSON 必须完整绑定为唯一的强类型 PageQuery 入参。 */
    @Test
    void shouldBindTypedPageQueryFromPostBody() throws Exception {
        when(application.pageUoms(any())).thenReturn(new PageResult<>(List.of(), 2, 25, 0, 0));

        mockMvc.perform(post("/api/mdm/uoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "params": {
                                    "categoryId": "123",
                                    "status": "ENABLED",
                                    "referenceUnit": false
                                  },
                                  "pageNo": 2,
                                  "pageSize": 25
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNo").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(25));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<PageQuery<UomPageParams>> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(application).pageUoms(captor.capture());
        assertThat(captor.getValue().params().categoryId()).isEqualTo("123");
        assertThat(captor.getValue().params().status()).isEqualTo("ENABLED");
        assertThat(captor.getValue().params().referenceUnit()).isFalse();
    }

    /** 旧 GET 集合分页入口必须删除，不能与 POST 查询协议并存。 */
    @Test
    void shouldRejectRemovedGetPaginationEndpoint() throws Exception {
        mockMvc.perform(get("/api/mdm/uoms"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** params 为 null 时 PageQuery 构造失败必须转换为稳定 400，而不是泄露 Jackson 异常。 */
    @Test
    void shouldRejectNullParamsWithStableBadRequest() throws Exception {
        mockMvc.perform(post("/api/mdm/uoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"params": null, "pageNo": 1, "pageSize": 20}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.pagination_invalid"))
                .andExpect(jsonPath("$.data[0].field").value("params"));
    }

    /** 缺失必填页码时必须作为不可读请求返回 400，不能把不完整请求交给业务查询。 */
    @Test
    void shouldRejectMissingPageNumberWithStableBadRequest() throws Exception {
        mockMvc.perform(post("/api/mdm/uoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"params": {}, "pageSize": 20}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid_body"));

        verify(application, never()).pageUoms(any());
    }

    /** Application 在 SQL 前拒绝配置超限时必须映射为稳定分页错误。 */
    @Test
    void shouldMapConfiguredMaximumFailureToBadRequest() throws Exception {
        when(application.pageUoms(any())).thenThrow(
                new PageQueryValidationException("pageSize", "每页条数不能超过 200"));

        mockMvc.perform(post("/api/mdm/uoms/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"params": {}, "pageNo": 1, "pageSize": 201}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.pagination_invalid"))
                .andExpect(jsonPath("$.data[0].field").value("pageSize"));
    }
}
