package io.github.chrisshi.mom.mdm.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MDM PostgreSQL 技术验证 Mapper。
 *
 * <p>接口只继承 MyBatis-Plus {@link BaseMapper}，不引入通用 Service 层，也不跨越 MDM Server 边界。
 * 正式领域 Mapper 后续应根据聚合边界决定使用通用方法还是显式 SQL，不能把 BaseMapper 暴露给
 * Controller 或其他领域模块。</p>
 */
@Mapper
public interface MdmDataProbeMapper extends BaseMapper<MdmDataProbeEntity> {
}
