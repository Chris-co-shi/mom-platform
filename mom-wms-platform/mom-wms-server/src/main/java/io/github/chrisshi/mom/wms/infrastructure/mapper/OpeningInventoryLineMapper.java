package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentLineEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 期初明细的普通 CRUD 复用 mom-data；涉及父单据归属的更新仍带数据库条件。 */
@Mapper
public interface OpeningInventoryLineMapper extends MomBaseMapper<OpeningDocumentLineEntity> {
    /** 在持有期初单 FOR UPDATE 行锁后分配行序号。 */
    @Select("SELECT COALESCE(MAX(line_no),0)+1 FROM wms_opening_document_line WHERE document_id=#{documentId}")
    int nextLineNo(@Param("documentId") String documentId);

    /** 保留明细所属单据约束；父单据状态由 Application 的行锁和状态校验保护。 */
    @Update("""
        UPDATE wms_opening_document_line SET
          material_id=#{line.materialId},material_code=#{line.materialCode},material_name=#{line.materialName},
          batch_code=#{line.batchCode},sn_code=#{line.snCode},location_id=#{line.locationId},
          location_code=#{line.locationCode},quality_status=#{line.qualityStatus},
          warehouse_status=#{line.warehouseStatus},quantity=#{line.quantity},uom_code=#{line.uomCode}
        WHERE id=#{line.id} AND document_id=#{line.documentId}
        """)
    int updateLine(@Param("line") OpeningDocumentLineEntity line);

    @Delete("DELETE FROM wms_opening_document_line WHERE id=#{lineId} AND document_id=#{documentId}")
    int deleteLine(@Param("documentId") String documentId,@Param("lineId") String lineId);

    @Delete("DELETE FROM wms_opening_document_line WHERE document_id=#{documentId}")
    int deleteLinesByDocument(@Param("documentId") String documentId);
}
