package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证 Controller 代理执行权限，不把前端菜单当作后端授权。 */
@SpringJUnitConfig(classes = {InventoryPermissionTest.SecurityConfig.class, InventoryController.class})
class InventoryPermissionTest {
    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean InventoryQueryService query() { return mock(InventoryQueryService.class); }
        @Bean InventoryFreezeApplication freezes() { return mock(InventoryFreezeApplication.class); }
        @Bean InventoryTransferApplication transfers() { return mock(InventoryTransferApplication.class); }
    }

    @Autowired InventoryController controller;
    @Autowired InventoryQueryService query;
    @Autowired InventoryTransferApplication transfers;

    private PageQuery<InventoryQueryService.OverviewFilter> request() {
        return new PageQuery<>(new InventoryQueryService.OverviewFilter(null, null, null), 1, 10);
    }

    @Test
    @WithMockUser(authorities = "mdm:material:read")
    void otherDomainPermissionCannotReadInventory() {
        assertThatThrownBy(() -> controller.overview(request())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "wms:inventory:read")
    void wmsReadPermissionCanReadInventory() {
        when(query.overview(any())).thenReturn(new PageResult<>(List.of(), 1, 10, 0, 0));
        assertThat(controller.overview(request()).data().records()).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "wms:inventory:read")
    void readPermissionCannotMoveInventory() {
        var request = new InventoryController.TransferRequest("move-1", "balance-1", "location-2", BigDecimal.ONE);
        assertThatThrownBy(() -> controller.transfer(request, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "wms:inventory:transfer")
    void transferPermissionCanMoveInventory() {
        var request = new InventoryController.TransferRequest("move-1", "balance-1", "location-2", BigDecimal.ONE);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        when(transfers.transfer(any())).thenReturn(new InventoryTransferApplication.TransferResult("tx-1"));
        assertThat(controller.transfer(request, authentication).data().transactionId()).isEqualTo("tx-1");
    }
}
