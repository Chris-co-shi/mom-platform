package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.OpeningDocumentLineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 期初明细 Mapper：普通插入、按条件更新和删除均复用 mom-data。
 * 仅保留依赖数据库顺序号计算、需要在父单据行锁下执行的特殊查询。
 */
@Mapper
public interface OpeningInventoryLineMapper extends MomBaseMapper<OpeningDocumentLineEntity> {
    /** 在 Application 已锁定期初单 FOR UPDATE 的事务中读取下一明细顺序号。 */
    @Select("SELECT COALESCE(MAX(line_no),0)+1 FROM wms_opening_document_line WHERE document_id=#{documentId}")
    int nextLineNo(@Param("documentId") String documentId);
}
