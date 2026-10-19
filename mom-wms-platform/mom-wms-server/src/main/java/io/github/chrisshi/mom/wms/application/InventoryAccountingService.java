package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingOperations;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingOperations.BalanceChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** WMS 唯一余额写入口；调用方需提供已核验的 MDM 快照及真实责任主体。 */
@Service
public class InventoryAccountingService {
    private final InventoryAccountingOperations mapper;
    private final IdGenerator ids;

    /**
     * @param mapper 同库原子 SQL 入口
     * @param ids MOM 字符串技术主键生成器
     */
    public InventoryAccountingService(InventoryAccountingOperations mapper, IdGenerator ids) {
        this.mapper = mapper;
        this.ids = ids;
    }

    /**
     * 一条库存唯一维度及记账时的展示快照；空批次或 SN 使用 null。
     * @param qualityStatus 记账时质量状态
     * @param warehouseStatus 记账时仓储状态
     */
    public record Dimension(String materialId, String materialCode, String materialName, String uomCode,
                            String batchCode, String snCode, String warehouseId, String warehouseCode,
                            String locationId, String locationCode, QualityStatus qualityStatus,
                            WarehouseStatus warehouseStatus) { }

    /**
     * 一条带符号的库存数量变化；正数入账，负数出账。
     * @param dimension 记账时库存维度快照
     * @param delta 有符号数量变化
     */
    public record Entry(Dimension dimension, BigDecimal delta) { }

    /**
     * 完整记账命令；requestKey 原样参与幂等匹配，不做 Trim 或大小写转换。
     * @param requestKey 幂等请求标识
     * @param transactionType 交易业务类型
     * @param sourceType 业务来源类型
     * @param sourceId 来源业务对象标识
     * @param operatorId 实际操作责任主体标识
     * @param entries 按业务顺序记账的分录
     */
    public record Posting(String requestKey, String transactionType, String sourceType, String sourceId,
                          String operatorId, List<Entry> entries) { }

    /**
     * 在一个 PostgreSQL REQUIRED 本地事务中写交易、更新余额并记录不可变分录快照。
     * 同键同内容重放返回原交易 ID，同键不同内容拒绝；唯一约束串行化同键并发，条件扣减禁止负库存。
     * 任一分录失败均抛出异常并回滚主表、此前分录和余额更新。分录按请求顺序逐条计算前后数量。
     *
     * @param posting 含真实来源、责任主体和已核验 MDM 快照的记账请求
     * @return 不可变交易 ID
     */
    @Transactional
    public String post(Posting posting) {
        validate(posting);
        String digest = digest(posting);
        String id = ids.nextId();
        String claimed = mapper.claim(id, posting.requestKey(), digest, posting.transactionType(),
                posting.sourceType(), posting.sourceId(), posting.operatorId());
        if (claimed == null) {
            if (digest.equals(mapper.hashOf(posting.requestKey()))) return mapper.idOf(posting.requestKey());
            throw new IllegalArgumentException("库存请求键已用于不同内容");
        }
        int line = 0;
        for (Entry entry : posting.entries()) {
            BalanceChange change = entry.delta().signum() > 0
                    ? mapper.increase(ids.nextId(), entry.dimension(), entry.delta())
                    : mapper.decrease(entry.dimension(), entry.delta());
            if (change == null) throw new IllegalStateException("库存不足或库位所属仓库冲突");
            if (mapper.insertEntry(ids.nextId(), claimed, ++line, change.id(), entry.dimension(), entry.delta(),
                    change.beforeQuantity(), change.afterQuantity()) != 1)
                throw new IllegalStateException("库存分录写入失败");
        }
        return claimed;
    }

    private static void validate(Posting posting) {
        if (posting == null || !present(posting.requestKey()) || posting.requestKey().length() > 128
                || !present(posting.transactionType()) || posting.transactionType().length() > 64
                || !present(posting.sourceType()) || posting.sourceType().length() > 64
                || !present(posting.sourceId()) || posting.sourceId().length() > 128
                || !present(posting.operatorId()) || posting.operatorId().length() > 128
                || posting.entries() == null || posting.entries().isEmpty() || posting.entries().size() > 100)
            throw new IllegalArgumentException("库存请求或条目数量非法");
        for (Entry entry : posting.entries()) {
            if (entry == null || entry.dimension() == null || entry.delta() == null
                    || entry.delta().signum() == 0 || entry.delta().scale() > 6
                    || entry.delta().precision() - entry.delta().scale() > 18)
                throw new IllegalArgumentException("库存数量非法");
            Dimension d = entry.dimension();
            if (!present(d.materialId()) || !present(d.materialCode()) || !present(d.materialName())
                    || !present(d.uomCode()) || !present(d.warehouseId()) || !present(d.warehouseCode())
                    || !present(d.locationId()) || !present(d.locationCode())
                    || d.qualityStatus() == null || d.warehouseStatus() == null
                    || "".equals(d.batchCode()) || "".equals(d.snCode()))
                throw new IllegalArgumentException("库存维度非法");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String digest(Posting posting) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            write(output, posting.transactionType());
            write(output, posting.sourceType());
            write(output, posting.sourceId());
            write(output, posting.operatorId());
            output.writeInt(posting.entries().size());
            for (Entry entry : posting.entries()) {
                Dimension d = entry.dimension();
                for (String value : new String[]{d.materialId(), d.materialCode(), d.materialName(), d.uomCode(),
                    d.batchCode(), d.snCode(), d.warehouseId(), d.warehouseCode(), d.locationId(),
                    d.locationCode(), d.qualityStatus().name(), d.warehouseStatus().name(),
                    entry.delta().stripTrailingZeros().toPlainString()}) write(output, value);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算库存请求指纹", exception);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        if (value == null) output.writeInt(-1);
        else {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            output.writeInt(encoded.length);
            output.write(encoded);
        }
    }
}
