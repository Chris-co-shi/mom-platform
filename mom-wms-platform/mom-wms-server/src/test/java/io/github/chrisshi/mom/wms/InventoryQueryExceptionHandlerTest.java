package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryQueryExceptionHandlerTest {
    private final InventoryQueryExceptionHandler handler = new InventoryQueryExceptionHandler(
            (I18nMessageResolver) (key, locale, args) -> key);

    @Test void missingPlantTimeZoneReturnsActionableStableClientError() {
        var response = handler.invalidReference(
                new OpeningInventoryReferenceException("PLANT_TIME_ZONE_MISSING"), Locale.SIMPLIFIED_CHINESE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).satisfies(body -> {
            assertThat(body.code()).isEqualTo("wms.opening.reference_invalid");
            assertThat(body.message()).isEqualTo("wms.error.opening_plant_timezone_missing");
        });
    }
}
