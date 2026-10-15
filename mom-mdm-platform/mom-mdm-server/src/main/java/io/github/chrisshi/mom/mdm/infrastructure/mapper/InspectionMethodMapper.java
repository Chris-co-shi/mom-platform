package io.github.chrisshi.mom.mdm.infrastructure.mapper;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.InspectionMethodEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 检验方法的 MDM 持久化入口。 */
@Mapper
public interface InspectionMethodMapper extends MomBaseMapper<InspectionMethodEntity> {
    /** 按主键锁定方法，保证质量标准写入与方法停用串行。 */
    @Select("SELECT * FROM mdm_inspection_method WHERE id = #{id} FOR UPDATE")
    InspectionMethodEntity selectByIdForUpdate(@Param("id") String id);
}
