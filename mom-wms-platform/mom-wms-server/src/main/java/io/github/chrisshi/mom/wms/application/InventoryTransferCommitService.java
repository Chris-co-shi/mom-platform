package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Entry;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Posting;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication.TransferCommand;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication.TransferResult;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceService.LocationSnapshot;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.Balance;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.TransferReplay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

/**
 * 库位移动的 PostgreSQL 本地原子提交边界。复核实时余额并交由唯一记账入口写交易、负/正分录及余额；
 * 不在事务内调用 MDM，也不创建移库单或修改库存状态。
 */
@Service
public class InventoryTransferCommitService {
    private final InventoryAccountingMapper operations;
    private final InventoryAccountingService accounting;

    /** @param operations PostgreSQL 锁及余额读取入口 @param accounting 唯一库存记账入口 */
    public InventoryTransferCommitService(InventoryAccountingMapper operations, InventoryAccountingService accounting) {
        this.operations = operations;
        this.accounting = accounting;
    }

    /**
     * 在 REQUIRED 本地事务内按请求键、库位、SN 顺序锁定，复核余额并完成两条分录。
     * 任一校验或分录失败均使交易和两个库位的余额整体回滚；同键并发在请求锁内重放。
     * @param command 已校验的移动命令 @param warehouseId 事务外验证时来源仓库 ID
     * @param target 已由 MDM 验证属于该仓库的目标库位快照
     * @return 本次或已提交的交易 ID
     * @throws InventoryTransferConflictException 实时余额、仓库状态、SN 物理约束或数量不满足要求
     */
    @Transactional
    public TransferResult transfer(TransferCommand command, String warehouseId, LocationSnapshot target) {
        operations.lockAccountingKey("WMS:REQUEST:" + command.requestKey());
        TransferReplay previous = operations.findTransferReplay(command.requestKey());
        if (previous != null) return InventoryTransferApplication.replay(previous, command);

        Balance source = operations.findInventoryBalance(command.balanceId());
        if (source == null || !warehouseId.equals(source.warehouseId())
                || source.locationId().equals(target.id())
                || source.warehouseStatus() != WarehouseStatus.NORMAL || source.quantity().signum() <= 0)
            throw new InventoryTransferConflictException("来源库存或库位状态已变化");

        // 与统一记账入口保持仓库 → 库位 → SN 的锁顺序，盘点开始时不会与移库形成反向等待。
        if (operations.countLockOwner(warehouseId) != null)
            throw new InventoryTransferConflictException("仓库正在盘点，不能移动库存");
        operations.lockAccountingKey("WMS:WH:" + warehouseId);
        if (operations.countLockOwner(warehouseId) != null)
            throw new InventoryTransferConflictException("仓库正在盘点，不能移动库存");

        lockLocations(source.locationId(), target.id());
        source = verifySn(command, source);
        Dimension from = dimension(source, source.locationId(), source.locationCode());
        Dimension to = dimension(source, target.id(), target.code());
        String transactionId;
        try {
            transactionId = accounting.post(new Posting(command.requestKey(), "LOCATION_TRANSFER", "MANUAL",
                    command.requestKey(), command.operatorId(), List.of(
                    new Entry(from, command.quantity().negate()), new Entry(to, command.quantity()))));
        } catch (IllegalStateException exception) {
            throw new InventoryTransferConflictException("库存数量或仓库生命周期不允许移动");
        }
        return new TransferResult(transactionId);
    }

    private void lockLocations(String sourceId, String targetId) {
        // 双向移动都以同一顺序锁定两个库位，避免相反方向各持一条余额行后互相等待。
        Stream.of(sourceId, targetId).distinct().sorted()
                .forEach(id -> operations.lockAccountingKey("WMS:LOCATION:" + id));
    }

    private Balance verifySn(TransferCommand command, Balance source) {
        if (source.snCode() == null) return source;
        operations.lockAccountingKey("WMS:SN:" + source.snCode());
        Balance current = operations.findInventoryBalance(command.balanceId());
        if (current == null || current.quantity().signum() <= 0 || current.warehouseStatus() != WarehouseStatus.NORMAL)
            throw new InventoryTransferConflictException("来源库存已变化");
        if (operations.positiveBalanceCountForSn(current.snCode()) != 1
                || command.quantity().compareTo(current.quantity()) != 0)
            throw new InventoryTransferConflictException("SN 标签无可分割语义，仅允许唯一正余额整体移动");
        return current;
    }

    private static Dimension dimension(Balance balance, String locationId, String locationCode) {
        return new Dimension(balance.materialId(), balance.materialCode(), balance.materialName(), balance.uomCode(),
                balance.batchCode(), balance.snCode(), balance.warehouseId(), balance.warehouseCode(),
                locationId, locationCode, balance.qualityStatus(), balance.warehouseStatus());
    }
}
