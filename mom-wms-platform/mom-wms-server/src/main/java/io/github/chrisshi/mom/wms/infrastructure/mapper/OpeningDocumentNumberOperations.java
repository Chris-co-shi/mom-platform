package io.github.chrisshi.mom.wms.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** WMS 期初单规则身份锁和事务型流水的本地 SQL 操作。 */
@Mapper
public interface OpeningDocumentNumberOperations {
    /**
     * 按固定规则编码锁定身份行，使发号与修订发布串行化。
     * @param ruleCode WMS 规则编码
     * @return 规则身份 ID，不存在时为空
     */
    @Select("SELECT id FROM wms_code_rule WHERE rule_code=#{ruleCode} FOR UPDATE")
    String lockRuleId(@Param("ruleCode") String ruleCode);

    /**
     * 在调用方事务中分配全局递增流水，回滚时不消耗编号。
     * @param ruleId WMS 规则身份 ID
     * @return 新的流水值
     */
    @Select("""
            INSERT INTO wms_code_rule_sequence(rule_id,last_value) VALUES (#{ruleId},1)
            ON CONFLICT(rule_id) DO UPDATE SET last_value=wms_code_rule_sequence.last_value+1
            RETURNING last_value
            """)
    Long next(@Param("ruleId") String ruleId);
}
