package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.mdm.infrastructure.mapper.InspectionCharacteristicMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.InspectionMethodMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.QualitySpecificationMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.SpecificationRevisionMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 质量锁定查询必须显式投影完整持久化字段，并保留主键过滤及行锁。 */
class QualityMapperProjectionTest {

    @Test
    void characteristicMustSelectAllMappedColumnsExplicitly() throws NoSuchMethodException {
        assertProjection(InspectionCharacteristicMapper.class, "mdm_inspection_characteristic",
                "id, code, name, default_uom_id, default_uom_version, enabled, version, "
                        + "created_at, created_by, updated_at, updated_by");
    }

    @Test
    void methodMustSelectAllMappedColumnsExplicitly() throws NoSuchMethodException {
        assertProjection(InspectionMethodMapper.class, "mdm_inspection_method",
                "id, code, name, description, enabled, version, created_at, created_by, updated_at, updated_by");
    }

    @Test
    void specificationMustSelectAllMappedColumnsExplicitly() throws NoSuchMethodException {
        assertProjection(QualitySpecificationMapper.class, "mdm_quality_specification",
                "id, code, name, plant_id, material_id, inspection_usage, plant_version, "
                        + "material_revision_no, material_version, created_at, created_by, updated_at, updated_by");
    }

    @Test
    void revisionMustSelectAllMappedColumnsExplicitly() throws NoSuchMethodException {
        assertProjection(SpecificationRevisionMapper.class, "mdm_specification_revision",
                "id, specification_id, revision_no, status, lock_version, plant_version, "
                        + "material_revision_no, material_version, released_at, obsolete_at, "
                        + "created_at, created_by, updated_at, updated_by");
    }

    private static void assertProjection(Class<?> mapper, String table, String columns)
            throws NoSuchMethodException {
        String sql = String.join(" ", mapper.getMethod("selectByIdForUpdate", String.class)
                .getAnnotation(Select.class).value()).replaceAll("\\s+", " ").trim();
        int from = sql.indexOf(" FROM ");
        assertThat(from).isPositive();
        assertThat(sql.substring(0, from)).startsWith("SELECT ");
        assertThat(sql.substring("SELECT ".length(), from).split("\\s*,\\s*"))
                .containsExactlyInAnyOrder(columns.split(", "));
        assertThat(sql.substring(from)).isEqualTo(" FROM " + table + " WHERE id = #{id} FOR UPDATE");
    }
}
