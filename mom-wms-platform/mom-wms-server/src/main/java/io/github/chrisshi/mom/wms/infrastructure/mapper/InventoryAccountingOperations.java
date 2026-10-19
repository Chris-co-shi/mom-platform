package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 库存记账的受限持久化入口，只暴露记账所需原子操作，不暴露余额 Entity 的通用 CRUD。 */
@Component
public class InventoryAccountingOperations {
    private final InventoryAccountingMapper mapper;

    /**
     * @param mapper 包内 PostgreSQL Mapper；不直接注入业务 Application
     */
    public InventoryAccountingOperations(InventoryAccountingMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 领取幂等请求并保存交易来源与责任主体。
     * @param id 新交易技术标识
     * @param requestKey 幂等请求标识
     * @param requestHash 完整请求指纹
     * @param transactionType 交易业务类型
     * @param sourceType 业务来源类型
     * @param sourceId 来源对象标识
     * @param operatorId 实际操作责任主体
     * @return 新领取交易标识；请求键冲突时为空
     */
    public String claim(String id, String requestKey, String requestHash, String transactionType,
                        String sourceType, String sourceId, String operatorId) {
        return mapper.claim(id, requestKey, requestHash, transactionType, sourceType, sourceId, operatorId);
    }

    /**
     * 查询幂等请求指纹。
     * @param requestKey 幂等请求标识
     * @return 已提交请求指纹
     */
    public String hashOf(String requestKey) {
        return mapper.hashOf(requestKey);
    }

    /**
     * 查询幂等请求的原交易标识。
     * @param requestKey 幂等请求标识
     * @return 已提交交易标识
     */
    public String idOf(String requestKey) {
        return mapper.idOf(requestKey);
    }

    /**
     * 查询 WMS 仓库库存生命周期状态；MDM 仓库静态状态不由此操作。
     * @param warehouseId MDM 仓库稳定 ID @return WMS 状态，不存在时为空
     */
    public String warehouseInventoryState(String warehouseId) {
        return mapper.warehouseInventoryState(warehouseId);
    }

    /**
     * 原子增加余额并返回执行前后的数量。
     * @param id 新余额技术标识
     * @param dimension 库存唯一维度与展示快照
     * @param delta 正数增加量
     * @return 更新结果；仓库归属冲突时为空
     */
    public BalanceChange increase(String id, Dimension dimension, BigDecimal delta) {
        return mapper.increase(id, dimension, delta);
    }

    /**
     * 条件扣减余额并返回执行前后的数量。
     * @param dimension 库存唯一维度
     * @param delta 负数扣减量
     * @return 更新结果；库存不足或余额不存在时为空
     */
    public BalanceChange decrease(Dimension dimension, BigDecimal delta) {
        return mapper.decrease(dimension, delta);
    }

    /**
     * 保存不可变分录及其独立历史快照。
     * @param id 分录技术标识
     * @param transactionId 所属交易标识
     * @param lineNo 请求内顺序行号
     * @param balanceId 受影响余额技术标识
     * @param dimension 记账发生时的库存维度
     * @param delta 有符号数量变化
     * @param beforeQuantity 分录作用前数量
     * @param afterQuantity 分录作用后数量
     * @return 插入行数
     */
    public int insertEntry(String id, String transactionId, int lineNo, String balanceId, Dimension dimension,
                           BigDecimal delta, BigDecimal beforeQuantity, BigDecimal afterQuantity) {
        return mapper.insertEntry(id, transactionId, lineNo, balanceId, dimension, delta,
                beforeQuantity, afterQuantity);
    }

    /** 原子余额操作返回的余额标识及前后数量。 */
    public record BalanceChange(String id, BigDecimal beforeQuantity, BigDecimal afterQuantity) { }
}
