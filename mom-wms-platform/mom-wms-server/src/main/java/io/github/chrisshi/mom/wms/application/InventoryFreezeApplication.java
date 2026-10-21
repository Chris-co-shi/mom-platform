package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.Balance;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.FreezeRecord;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.UnfreezeRecord;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** WMS 仓储冻结限制用例；只改变 WarehouseStatus，通过库存唯一记账入口保持余额、交易、分录及限制原子一致。 */
@Service
public class InventoryFreezeApplication {
    private final InventoryAccountingMapper operations;
    private final InventoryAccountingService accounting;
    private final IdGenerator ids;

    /**
     * @param operations 库存记账与冻结限制的受限 PostgreSQL 原子操作入口
     * @param accounting WMS 唯一库存余额过账入口
     * @param ids MOM 字符串技术主键生成器
     */
    public InventoryFreezeApplication(InventoryAccountingMapper operations, InventoryAccountingService accounting,
                                      IdGenerator ids) {
        this.operations = operations;
        this.accounting = accounting;
        this.ids = ids;
    }

    /** 冻结原因受 WMS 业务语义约束；字典展示不能替代服务端允许值校验。 */
    public enum FreezeReason {
        /** 包装破损，需要隔离实物。 */ PACKAGE_DAMAGE,
        /** 账面与现场库存存在差异，等待核实。 */ STOCK_DISCREPANCY,
        /** 仓储作业发现异常，等待核实。 */ WAREHOUSE_CHECK,
        /** 出于仓储安全限制暂停使用。 */ SAFETY_HOLD,
        /** 其他仓储原因；必须提供具体说明。 */ OTHER
    }

    /** 冻结命令；调用方传入经授权确认的库存余额 ID、业务来源和真实操作责任主体。 */
    public record FreezeCommand(String requestKey, String balanceId, BigDecimal quantity, FreezeReason reason,
                                String note, String sourceType, String sourceId, String operatorId) { }

    /** 解冻命令必须指向一条具体冻结限制，不以 SN 或状态批量解冻。 */
    public record UnfreezeCommand(String requestKey, String freezeId, BigDecimal quantity, String note,
                                  String sourceType, String sourceId, String operatorId) { }

    /** 当前有效限制视图，包含原始库存维度、原因、来源与操作者快照。 */
    public record FreezeView(String id, String transactionId, String originalBalanceId, String materialId,
                             String materialCode, String materialName, String uomCode, String batchCode,
                             String snCode, String warehouseId, String warehouseCode, String locationId,
                             String locationCode, QualityStatus qualityStatus, String reasonCode, String note,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal originalQuantity,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remainingQuantity, String sourceType,
                             String sourceId, String operatorId, Instant frozenAt) { }

    /** 解冻结果；剩余量为所指限制的最新数量。 */
    public record UnfreezeResult(String transactionId, String freezeId,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remainingQuantity) { }

    /**
     * 对选定 NORMAL 余额执行部分或全部冻结。两条相反分录、交易及原因限制在 REQUIRED 本地事务提交。
     * 同键同内容重放返回相同限制；余额条件扣减失败、仓库非 ACTIVE 或任何持久化失败均回滚。
     * @param command 冻结业务命令
     * @return 冻结限制事实
     * @throws IllegalArgumentException 原因、数量、请求标识或业务来源非法
     * @throws IllegalStateException 库存不足、余额非正常状态或仓库生命周期不允许操作
     */
    @Transactional
    public FreezeView freezeInventory(FreezeCommand command) {
        validateCommon(command == null ? null : command.requestKey(), command == null ? null : command.quantity(),
                command == null ? null : command.sourceType(), command == null ? null : command.sourceId(),
                command == null ? null : command.operatorId());
        if (command.balanceId() == null || command.balanceId().isBlank() || command.reason() == null
                || command.note() != null && command.note().length() > 500
                || command.reason() == FreezeReason.OTHER && (command.note() == null || command.note().isBlank()))
            throw new IllegalArgumentException("冻结库存、原因或说明非法");
        Balance balance = operations.findInventoryBalance(command.balanceId());
        if (balance == null || balance.warehouseStatus() != WarehouseStatus.NORMAL)
            throw new IllegalStateException("只能选择正常仓储状态的库存冻结");

        String transactionId = accounting.post(new InventoryAccountingService.Posting(command.requestKey(),
                "FREEZE", command.sourceType(), command.sourceId(), command.operatorId(), List.of(
                new InventoryAccountingService.Entry(dimension(balance, WarehouseStatus.NORMAL), command.quantity().negate()),
                new InventoryAccountingService.Entry(dimension(balance, WarehouseStatus.FROZEN), command.quantity()))));
        FreezeRecord existing = operations.findFreezeByTransaction(transactionId);
        if (existing != null) return replayFreeze(existing, balance, command);
        String id = ids.nextId();
        if (operations.insertFreeze(id, transactionId, balance, command.reason().name(), clean(command.note()),
                command.quantity(), command.sourceType(), command.sourceId(), command.operatorId()) != 1) {
            existing = operations.findFreezeByTransaction(transactionId);
            if (existing != null) return replayFreeze(existing, balance, command);
            throw new IllegalStateException("冻结限制写入失败");
        }
        return view(operations.findFreezeByTransaction(transactionId));
    }

    /**
     * 按库存维度读取所有有效限制；相同 SN 的多笔冻结保持独立并可分别选择解冻。
     * @param balanceId 实时库存明细余额 ID
     * @return 按冻结时间和技术 ID 排序的有效限制
     * @throws IllegalArgumentException 库存余额不存在
     */
    @Transactional(readOnly = true)
    public List<FreezeView> queryActiveFreezes(String balanceId) {
        if (balanceId == null || balanceId.isBlank()) throw new IllegalArgumentException("库存余额标识不能为空");
        Balance balance = operations.findInventoryBalance(balanceId);
        if (balance == null) throw new IllegalArgumentException("库存余额不存在");
        return operations.activeFreezesByDimension(balance).stream().map(InventoryFreezeApplication::view).toList();
    }

    /**
     * 对明确冻结限制执行部分或全部解冻。冻结行锁与剩余量条件更新保护并发，历史请求键保证重放不重复记账。
     * 余额、交易、两条分录、限制剩余量和解冻历史在同一 REQUIRED 本地事务提交。
     * @param command 解冻业务命令
     * @return 解冻交易和该限制最新剩余量
     * @throws IllegalArgumentException 命令非法或同一请求键内容不一致
     * @throws IllegalStateException 冻结限制不存在、数量超限或仓库生命周期不允许操作
     */
    @Transactional
    public UnfreezeResult unfreezeInventory(UnfreezeCommand command) {
        validateCommon(command == null ? null : command.requestKey(), command == null ? null : command.quantity(),
                command == null ? null : command.sourceType(), command == null ? null : command.sourceId(),
                command == null ? null : command.operatorId());
        if (command.freezeId() == null || command.freezeId().isBlank() || !present(command.note())
                || command.note().length() > 500)
            throw new IllegalArgumentException("解冻限制或说明非法");
        String requestHash = digest(command);
        UnfreezeRecord replay = operations.findUnfreeze(command.requestKey());
        if (replay != null) return replay(replay, requestHash, command.freezeId());

        FreezeRecord freeze = operations.lockFreeze(command.freezeId());
        if (freeze == null) throw new IllegalStateException("冻结限制不存在");
        // 同一冻结限制的重复请求可能在首次查询后等待了行锁，锁内必须再次检查幂等事实。
        replay = operations.findUnfreeze(command.requestKey());
        if (replay != null) return replay(replay, requestHash, command.freezeId());
        if (command.quantity().compareTo(freeze.remainingQuantity()) > 0)
            throw new IllegalStateException("解冻数量超过该限制的剩余冻结数量");
        if (operations.decreaseFreezeRemaining(freeze.id(), command.quantity()) != 1)
            throw new IllegalStateException("冻结限制数量已被并发变更");

        Balance original = new Balance(freeze.originalBalanceId(), freeze.materialId(), freeze.materialCode(),
                freeze.materialName(), freeze.uomCode(), freeze.batchCode(), freeze.snCode(), freeze.warehouseId(),
                freeze.warehouseCode(), freeze.locationId(), freeze.locationCode(), freeze.qualityStatus(),
                WarehouseStatus.NORMAL, freeze.originalQuantity());
        // 所有冻结/解冻均先锁定 NORMAL 再锁定 FROZEN，避免互相反向锁余额行。
        String transactionId = accounting.post(new InventoryAccountingService.Posting(command.requestKey(),
                "UNFREEZE", command.sourceType(), command.sourceId(), command.operatorId(), List.of(
                new InventoryAccountingService.Entry(dimension(original, WarehouseStatus.NORMAL), command.quantity()),
                new InventoryAccountingService.Entry(dimension(original, WarehouseStatus.FROZEN), command.quantity().negate()))));
        operations.insertUnfreeze(ids.nextId(), command.requestKey(), requestHash, freeze.id(), transactionId,
                command.quantity(), clean(command.note()), command.sourceType(), command.sourceId(), command.operatorId());
        FreezeRecord updated = operations.lockFreeze(freeze.id());
        return new UnfreezeResult(transactionId, freeze.id(), updated.remainingQuantity());
    }

    private UnfreezeResult replay(UnfreezeRecord replay, String requestHash, String freezeId) {
        if (!replay.requestHash().equals(requestHash)) throw new IllegalArgumentException("解冻请求键已用于不同内容");
        FreezeRecord freeze = operations.lockFreeze(freezeId);
        if (freeze == null) throw new IllegalStateException("冻结限制不存在");
        return new UnfreezeResult(replay.transactionId(), freezeId, freeze.remainingQuantity());
    }

    private static FreezeView replayFreeze(FreezeRecord existing, Balance balance, FreezeCommand command) {
        if (!existing.originalBalanceId().equals(balance.id())
                || existing.originalQuantity().compareTo(command.quantity()) != 0
                || !existing.reasonCode().equals(command.reason().name())
                || !java.util.Objects.equals(existing.note(), clean(command.note()))
                || !existing.sourceType().equals(command.sourceType())
                || !existing.sourceId().equals(command.sourceId())
                || !existing.operatorId().equals(command.operatorId()))
            throw new IllegalArgumentException("冻结请求键已用于不同内容");
        return view(existing);
    }

    private static InventoryAccountingService.Dimension dimension(Balance balance, WarehouseStatus status) {
        return new InventoryAccountingService.Dimension(balance.materialId(), balance.materialCode(), balance.materialName(),
                balance.uomCode(), balance.batchCode(), balance.snCode(), balance.warehouseId(), balance.warehouseCode(),
                balance.locationId(), balance.locationCode(), balance.qualityStatus(), status);
    }

    private static FreezeView view(FreezeRecord record) {
        return new FreezeView(record.id(), record.transactionId(), record.originalBalanceId(), record.materialId(),
                record.materialCode(), record.materialName(), record.uomCode(), record.batchCode(), record.snCode(),
                record.warehouseId(), record.warehouseCode(), record.locationId(), record.locationCode(),
                record.qualityStatus(), record.reasonCode(), record.note(), record.originalQuantity(),
                record.remainingQuantity(), record.sourceType(), record.sourceId(), record.operatorId(), record.frozenAt());
    }

    private static void validateCommon(String requestKey, BigDecimal quantity, String sourceType,
                                       String sourceId, String operatorId) {
        if (!present(requestKey) || requestKey.length() > 128 || quantity == null || quantity.signum() <= 0
                || quantity.scale() > 6 || quantity.precision() - quantity.scale() > 18
                || !present(sourceType) || sourceType.length() > 64 || !present(sourceId) || sourceId.length() > 128
                || !present(operatorId) || operatorId.length() > 128)
            throw new IllegalArgumentException("冻结或解冻请求字段非法");
    }

    private static String digest(UnfreezeCommand command) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            for (String value : new String[]{command.freezeId(), command.quantity().stripTrailingZeros().toPlainString(),
                    clean(command.note()), command.sourceType(), command.sourceId(), command.operatorId()}) {
                if (value == null) output.writeInt(-1);
                else {
                    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算解冻请求指纹", exception);
        }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
