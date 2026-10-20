package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证仓库未启用错误跨 HTTP 边界保留可识别代码和明确文案。 */
class InventoryCountExceptionHandlerTest {
    @Test
    void warehouseInactiveHasSpecificConflictResponse() {
        I18nMessageResolver messages=mock(I18nMessageResolver.class);
        when(messages.resolve("wms.error.count_warehouse_inactive",Locale.SIMPLIFIED_CHINESE))
            .thenReturn("请先完成期初建账并启用仓库库存");
        var response=new InventoryCountExceptionHandler(messages).warehouseInactive(Locale.SIMPLIFIED_CHINESE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("wms.inventory.count_warehouse_inactive");
        assertThat(response.getBody().message()).isEqualTo("请先完成期初建账并启用仓库库存");
    }
}
