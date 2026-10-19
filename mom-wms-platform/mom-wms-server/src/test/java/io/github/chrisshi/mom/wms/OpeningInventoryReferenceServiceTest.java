package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.mdm.api.LocationReference;
import io.github.chrisshi.mom.mdm.api.MaterialReference;
import io.github.chrisshi.mom.mdm.api.UomReference;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.mdm.client.MdmReferenceClient;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpeningInventoryReferenceServiceTest {
    private final MdmReferenceClient mdm = mock(MdmReferenceClient.class);
    private final OpeningInventoryReferenceService references = new OpeningInventoryReferenceService(mdm);

    @Test void rejectsUnavailableWarehouseAndMaterialAndMismatchedLocationParent() {
        when(mdm.getWarehouseReference("warehouse")).thenReturn(Result.success(new WarehouseResourceReference(
                "WAREHOUSE", "warehouse", "WH-1", "主仓", "plant", "plant", "Asia/Shanghai", null, 0L,
                false, "WAREHOUSE_DISABLED")));
        assertThatThrownBy(() -> references.warehouse("warehouse"))
                .isInstanceOf(OpeningInventoryReferenceException.class)
                .extracting(error -> ((OpeningInventoryReferenceException) error).invalidReason())
                .isEqualTo("WAREHOUSE_DISABLED");

        when(mdm.getMaterialReference("material")).thenReturn(Result.success(new MaterialReference("material",
                "MAT-1", "物料", "category", "uom", "RAW_MATERIAL", "REQUIRED", null, 1, 0L,
                false, "MATERIAL_DISABLED")));
        assertThatThrownBy(() -> references.material("material")).isInstanceOf(IllegalArgumentException.class);

        when(mdm.getLocationReference("location")).thenReturn(Result.success(new LocationReference("location",
                "LOC-1", "库位", "plant", "Asia/Shanghai", "area", "type", null, 0L, true, null)));
        when(mdm.getWarehouseAreaReference("area")).thenReturn(Result.success(new WarehouseResourceReference(
                "WAREHOUSE_AREA", "area", "AREA-1", "库区", "other-warehouse", "plant", "Asia/Shanghai",
                null, 0L, true, null)));
        assertThatThrownBy(() -> references.location("location", "warehouse"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void reportsMissingPlantTimeZoneAsTheWarehouseInvalidReason() {
        when(mdm.getWarehouseReference("warehouse")).thenReturn(Result.success(new WarehouseResourceReference(
                "WAREHOUSE", "warehouse", "WH-1", "主仓", "plant", "plant", null, null, 0L,
                false, "PLANT_TIME_ZONE_MISSING")));

        assertThatThrownBy(() -> references.warehouse("warehouse"))
                .isInstanceOf(OpeningInventoryReferenceException.class)
                .extracting(error -> ((OpeningInventoryReferenceException) error).invalidReason())
                .isEqualTo("PLANT_TIME_ZONE_MISSING");
    }

    @Test void resolvesMaterialInventoryUomFromMdmAndKeepsBatchPolicy() {
        when(mdm.getMaterialReference("material")).thenReturn(Result.success(new MaterialReference("material",
                "MAT-1", "物料", "category", "uom", "RAW_MATERIAL", "REQUIRED", null, 1, 0L,
                true, null)));
        when(mdm.getUomReference("uom")).thenReturn(Result.success(new UomReference("uom", "kg", "千克", "kg",
                "mass", "dimension", true, null, 0L, true, null)));
        var snapshot = references.material("material");
        assertThat(snapshot.batchPolicy()).isEqualTo("REQUIRED");
        assertThat(snapshot.uomCode()).isEqualTo("kg");
    }
}
