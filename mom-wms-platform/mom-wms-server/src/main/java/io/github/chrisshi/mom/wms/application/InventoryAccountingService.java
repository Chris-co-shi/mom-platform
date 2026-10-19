package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper;
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

/**
 * WMS 唯一余额写入口；调用方提供已核验的 MDM 引用快照，当前仅供受控期初数据和测试调用。
 */
@Service
public class InventoryAccountingService {
    private final InventoryAccountingMapper mapper;
    private final IdGenerator ids;

    /**
     * @param mapper 同库原子 SQL 入口 @param ids MOM 字符串技术主键生成器
     */
    public InventoryAccountingService(InventoryAccountingMapper mapper, IdGenerator ids) {
        this.mapper = mapper;
        this.ids = ids;
    }

    /**
     * 一条余额维度及其已核验的展示快照；空 Batch/SN 使用 null。
     */
    public record Dimension(String materialId, String materialCode, String materialName, String uomCode,
                            String batchCode, String snCode, String warehouseId, String warehouseCode,
                            String locationId, String locationCode, String qualityStatus, String warehouseStatus) {
    }

    /**
     * 一条带符号数量变化。
     */
    public record Entry(Dimension dimension, BigDecimal delta) {
    }

    /**
     * 完整业务请求；原始 requestKey 不做 Trim 或大小写转换。
     */
    public record Posting(String requestKey, List<Entry> entries) {
    }

    /**
     * 在单个 PostgreSQL REQUIRED 事务中追加交易、分录并原子变更余额。
     * 相同键与相同内容重放返回原交易 ID；不同内容冲突，超扣和引用维度冲突整体回滚。
     *
     * @param posting 已由调用方核验 MDM 引用的库存变化
     * @return 不可变交易 ID
     */
    @Transactional
    public String post(Posting posting) {
        validate(posting);
        String digest = digest(posting);
        String id = ids.nextId();
        String claimed = mapper.claim(id, posting.requestKey(), digest);
        if (claimed == null) {
            if (digest.equals(mapper.hashOf(posting.requestKey()))) return mapper.idOf(posting.requestKey());
            throw new IllegalArgumentException("库存请求键已用于不同内容");
        }
        int line = 0;
        for (Entry entry : posting.entries()) {
            String balanceId = entry.delta().signum() > 0
                    ? mapper.increase(ids.nextId(), entry.dimension(), entry.delta())
                    : mapper.decrease(entry.dimension(), entry.delta());
            if (balanceId == null) throw new IllegalStateException("库存不足或库位所属仓库冲突");
            if (mapper.insertEntry(ids.nextId(), claimed, ++line, balanceId, entry.delta()) != 1)
                throw new IllegalStateException("库存分录写入失败");
        }
        return claimed;
    }

    private static void validate(Posting posting) {
        if (posting == null || posting.requestKey() == null || posting.requestKey().isBlank()
            || posting.requestKey().length() > 128 || posting.entries() == null
            || posting.entries().isEmpty() || posting.entries().size() > 100)
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
                    || d.qualityStatus() == null
                    || !List.of("PENDING", "QUALIFIED", "RESTRICTED", "REJECTED").contains(d.qualityStatus())
                    || d.warehouseStatus() == null
                    || !List.of("NORMAL", "FROZEN").contains(d.warehouseStatus())
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
            output.writeInt(posting.entries().size());
            for (Entry entry : posting.entries()) {
                Dimension d = entry.dimension();
                for (String value : new String[]{d.materialId(), d.materialCode(), d.materialName(), d.uomCode(),
                    d.batchCode(), d.snCode(), d.warehouseId(), d.warehouseCode(), d.locationId(),
                    d.locationCode(), d.qualityStatus(), d.warehouseStatus(),
                    entry.delta().stripTrailingZeros().toPlainString()}) {
                    if (value == null) output.writeInt(-1);
                    else {
                        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                        output.writeInt(encoded.length);
                        output.write(encoded);
                    }
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法计算库存请求指纹", exception);
        }
    }
}
