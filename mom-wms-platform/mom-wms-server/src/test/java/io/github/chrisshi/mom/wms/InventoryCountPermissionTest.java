package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.wms.application.InventoryCountApplication;
import io.github.chrisshi.mom.wms.controller.InventoryCountController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 验证盘点差异确认与库存调整是两个独立的后端权限。 */
@SpringJUnitConfig(classes={InventoryCountPermissionTest.SecurityConfig.class,InventoryCountController.class})
class InventoryCountPermissionTest {
    @Configuration @EnableMethodSecurity
    static class SecurityConfig {
        @Bean InventoryCountApplication application() { return mock(InventoryCountApplication.class); }
    }
    @Autowired InventoryCountController controller;
    @Test @WithMockUser(authorities="wms:inventory-count:confirm")
    void confirmerCannotPostInventoryAdjustment() {
        assertThatThrownBy(()->controller.adjust("task-1")).isInstanceOf(AccessDeniedException.class);
    }
    @Test @WithMockUser(authorities="wms:inventory-count:adjust")
    void adjusterCannotConfirmDifferences() {
        assertThatThrownBy(()->controller.confirm("task-1")).isInstanceOf(AccessDeniedException.class);
    }
    @Test @WithMockUser(authorities="wms:inventory-count:read")
    void readerCannotCountStock() {
        assertThatThrownBy(()->controller.count("task-1","line-1",new InventoryCountApplication.CountRequest(null)))
            .isInstanceOf(AccessDeniedException.class);
    }
}
