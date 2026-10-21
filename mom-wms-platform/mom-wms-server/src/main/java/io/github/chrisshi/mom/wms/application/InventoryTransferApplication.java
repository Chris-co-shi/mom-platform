package io.github.chrisshi.mom.wms.application;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.Balance;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.TransferReplay;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 同仓库库位移动的非事务编排入口。负责请求校验、已提交请求重放及事务外 MDM 校验；
 * 不直接写余额，也不改变物料、质量或仓储状态。
 */
@Service
@RequiredArgsConstructor
public class InventoryTransferApplication {
    private final InventoryAccountingMapper operations;
    private final OpeningInventoryReferenceService references;
    private final InventoryTransferCommitService commits;

    

    /**
     * 执行同仓库库位移动；已成功请求按不可变分录重放，不再依赖当前库存或 MDM 状态。
     * @param command 来源余额、目标库位、数量、请求键及真实操作人
     * @return 原交易或本次交易标识
     * @throws IllegalArgumentException 输入非法或目标引用无效
     * @throws InventoryTransferConflictException 库存不足、SN 不可拆、状态、生命周期或幂等内容冲突
     */
    public TransferResult transfer(TransferCommand command) {
        validate(command);
        TransferReplay previous = operations.findTransferReplay(command.requestKey());
        if (previous != null) return replay(previous, command);
        Balance source = operations.findInventoryBalance(command.balanceId());
        if (source == null) throw new InventoryTransferConflictException("来源库存不存在");
        if (source.locationId().equals(command.targetLocationId()))
            throw new IllegalArgumentException("来源与目标库位不能相同");
        LocationSnapshot target = references.location(command.targetLocationId(), source.warehouseId());
        return commits.transfer(command, source.warehouseId(), target);
    }
    /** 只读原请求键的已提交移动事实；缺失时仍不能推断原请求未提交。 */
    public TransferLookup findCommitted(String requestKey, String operatorId) {
        if (!present(requestKey) || requestKey.length() > 128 || !present(operatorId))
            throw new IllegalArgumentException("库存移动请求键非法");
        TransferReplay previous = operations.findTransferReplay(requestKey);
        if (previous == null || !"LOCATION_TRANSFER".equals(previous.transactionType())
                || !"MANUAL".equals(previous.sourceType()) || !requestKey.equals(previous.sourceId())
                || !operatorId.equals(previous.operatorId())) return null;
        return new TransferLookup(previous.transactionId(), previous.balanceId(),
                previous.targetLocationId(), previous.quantity().toPlainString());
    }
    /** 原移动的不可变输入与交易标识。 */
    public record TransferLookup(String transactionId, String balanceId, String targetLocationId, String quantity) { }

    static TransferResult replay(TransferReplay previous, TransferCommand command) {
        if (!"LOCATION_TRANSFER".equals(previous.transactionType())
                || !"MANUAL".equals(previous.sourceType())
                || !command.requestKey().equals(previous.sourceId())
                || !command.operatorId().equals(previous.operatorId())
                || !command.balanceId().equals(previous.balanceId())
                || !command.targetLocationId().equals(previous.targetLocationId())
                || previous.quantity() == null || command.quantity().compareTo(previous.quantity()) != 0)
            throw new InventoryTransferConflictException("库存移动请求键已用于不同内容");
        return new TransferResult(previous.transactionId());
    }

    private static void validate(TransferCommand command) {
        if (command == null || !present(command.requestKey()) || command.requestKey().length() > 128
                || !present(command.balanceId()) || !present(command.targetLocationId())
                || !present(command.operatorId()) || command.operatorId().length() > 128
                || command.quantity() == null || command.quantity().signum() <= 0
                || command.quantity().scale() > 6
                || command.quantity().precision() - command.quantity().scale() > 18)
            throw new IllegalArgumentException("库存移动请求非法");
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    /** 移动请求；客户端不提供仓库、物料、批次、SN 或状态，避免越权更改维度。
     * @param requestKey 稳定请求键 @param balanceId 选中的具体来源余额
     * @param targetLocationId MDM 目标库位 ID @param quantity 正数移动数量 @param operatorId 已认证操作人 */
    public record TransferCommand(String requestKey, String balanceId, String targetLocationId,
                                  BigDecimal quantity, String operatorId) { }

    /** 移动成功后返回的不可变库存交易标识。 @param transactionId 库存交易 ID */
    public record TransferResult(String transactionId) { }
}
