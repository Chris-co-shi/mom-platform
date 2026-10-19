package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** WMS 库存记账与仓储冻结的受限持久化入口；不暴露余额 Entity 的通用 CRUD。 */
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

    /** 冻结操作选择的实时余额快照。 */
    public record Balance(String id, String materialId, String materialCode, String materialName, String uomCode,
                          String batchCode, String snCode, String warehouseId, String warehouseCode,
                          String locationId, String locationCode, QualityStatus qualityStatus,
                          WarehouseStatus warehouseStatus, BigDecimal quantity) { }

    /** 冻结限制完整快照；remainingQuantity 仅可通过锁定与条件更新修改。 */
    public record FreezeRecord(String id, String transactionId, String originalBalanceId, String materialId,
                               String materialCode, String materialName, String uomCode, String batchCode,
                               String snCode, String warehouseId, String warehouseCode, String locationId,
                               String locationCode, QualityStatus qualityStatus,
                               String reasonCode, String note, BigDecimal originalQuantity, BigDecimal remainingQuantity,
                               String sourceType, String sourceId, String operatorId, Instant frozenAt) { }

    /** 解冻请求幂等记录。 */
    public record UnfreezeRecord(String requestHash, String transactionId) { }

    /**
     * 按 ID 读取余额快照用于冻结选择；真正增减仍交由唯一库存记账入口完成。
     * @param id 实时库存余额 ID
     * @return 当前余额快照，不存在时为空
     */
    public Balance findInventoryBalance(String id) { return mapper.findInventoryBalance(id); }

    /**
     * 查询冻结交易对应限制，处理库存交易幂等重放。
     * @param transactionId 库存交易 ID
     * @return 冻结限制，不存在时为空
     */
    public FreezeRecord findFreezeByTransaction(String transactionId) { return mapper.findFreezeByTransaction(transactionId); }

    /**
     * 锁定指定限制行，串行化同一限制的解冻操作。
     * @param id 冻结限制 ID
     * @return 当前限制；不存在时为空
     */
    public FreezeRecord lockFreeze(String id) { return mapper.lockFreeze(id); }

    /**
     * 同一事务中保存冻结原因与库存维度快照。
     * @param id 冻结限制技术 ID @param transactionId 冻结库存交易 ID @param balance 原始 NORMAL 余额快照
     * @param reasonCode 受控仓储冻结原因 @param note 冻结说明 @param quantity 冻结数量
     * @param sourceType 业务来源类型 @param sourceId 来源业务标识 @param operatorId 实际操作人
     * @return 插入行数；同一交易已记录时为零
     */
    public int insertFreeze(String id, String transactionId, Balance balance, String reasonCode, String note,
                            BigDecimal quantity, String sourceType, String sourceId, String operatorId) {
        return mapper.insertFreeze(id, transactionId, balance, reasonCode, note, quantity, sourceType, sourceId, operatorId);
    }

    /**
     * 查询给定库存维度仍有效的所有冻结限制。
     * @param balance 实时库存维度快照
     * @return 尚有剩余冻结数量的限制，按时间和 ID 排序
     */
    public List<FreezeRecord> activeFreezesByDimension(Balance balance) { return mapper.activeFreezesByDimension(balance); }

    /**
     * 通过数据库剩余量条件更新保护并发解冻不超额。
     * @param id 冻结限制 ID @param quantity 本次解冻数量
     * @return 更新行数；剩余量不足时为零
     */
    public int decreaseFreezeRemaining(String id, BigDecimal quantity) { return mapper.decreaseFreezeRemaining(id, quantity); }

    /**
     * 查询已提交解冻幂等事实。
     * @param requestKey 原样请求幂等键
     * @return 解冻请求摘要与原交易标识，不存在时为空
     */
    public UnfreezeRecord findUnfreeze(String requestKey) { return mapper.findUnfreeze(requestKey); }

    /**
     * 追加不可变解冻历史。
     * @param id 解冻历史 ID @param requestKey 原样幂等键 @param requestHash 解冻请求摘要
     * @param freezeId 被解除的限制 ID @param transactionId 解冻库存交易 ID @param quantity 解冻数量
     * @param note 必填处理说明 @param sourceType 业务来源类型 @param sourceId 来源标识 @param operatorId 操作人
     * @return 插入行数
     */
    public int insertUnfreeze(String id, String requestKey, String requestHash, String freezeId, String transactionId,
                              BigDecimal quantity, String note, String sourceType, String sourceId, String operatorId) {
        return mapper.insertUnfreeze(id, requestKey, requestHash, freezeId, transactionId, quantity, note,
                sourceType, sourceId, operatorId);
    }
}
