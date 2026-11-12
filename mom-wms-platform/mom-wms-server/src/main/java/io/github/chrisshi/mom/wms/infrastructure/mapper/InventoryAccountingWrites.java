package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.infrastructure.mapper.InventoryAccountingMapper.BalanceChange;
import io.github.chrisshi.mom.wms.infrastructure.mapper.MysqlInventoryAccountingMapper.LockedBalance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 在记账应用事务内选择数据库写法，并返回同一份交易与余额契约。 */
@Component
public class InventoryAccountingWrites {
    private final InventoryAccountingMapper postgres;
    private final MysqlInventoryAccountingMapper mysql;
    private final boolean useMysql;

    /** 按启动时选定的数据库绑定记账写入路径。 */
    public InventoryAccountingWrites(InventoryAccountingMapper postgres, MysqlInventoryAccountingMapper mysql,
                                     @Value("${mom.database.vendor:postgresql}") String vendor) {
        this.postgres = postgres;
        this.mysql = mysql;
        this.useMysql = "mysql".equals(vendor);
    }

    /** 写入交易，目标请求键已存在时返回空，其他约束错误照常抛出。 */
    public String claim(String id, String requestKey, String requestHash, String transactionType,
                        String sourceType, String sourceId, String operatorId) {
        if (!useMysql) {
            return postgres.claim(id, requestKey, requestHash, transactionType, sourceType, sourceId, operatorId);
        }
        try {
            if (mysql.insertTransaction(id, requestKey, requestHash, transactionType, sourceType, sourceId, operatorId) != 1) {
                throw new IllegalStateException("库存交易写入失败");
            }
            return id;
        } catch (DuplicateKeyException failure) {
            if (WmsMysqlDuplicateKey.matches(failure, "wms_inventory_transaction", "uk_wms_inventory_transaction_request_key")) {
                return null;
            }
            throw failure;
        }
    }

    /** 增加指定维度余额，跨仓库归属冲突时返回空。 */
    public BalanceChange increase(String id, Dimension dimension, BigDecimal delta) {
        if (!useMysql) {
            return postgres.increase(id, dimension, delta);
        }
        LockedBalance existing = mysql.lockBalance(dimension);
        if (existing == null) {
            try {
                if (mysql.insertBalance(id, dimension, delta) != 1) {
                    throw new IllegalStateException("库存余额写入失败");
                }
                return new BalanceChange(id, BigDecimal.ZERO, mysql.quantity(id));
            } catch (DuplicateKeyException failure) {
                if (!WmsMysqlDuplicateKey.matches(failure, "wms_inventory_balance", "uk_wms_inventory_balance_dimension")) {
                    throw failure;
                }
                // 同一维度可能由另一个仓库先插入，锁住胜出的余额并按仓库归属处理。
                existing = mysql.lockBalance(dimension);
            }
        }
        return change(existing, dimension, delta);
    }

    /** 扣减指定维度余额，余额缺失、归属冲突或数量不足时返回空。 */
    public BalanceChange decrease(Dimension dimension, BigDecimal delta) {
        return useMysql ? change(mysql.lockBalance(dimension), dimension, delta) : postgres.decrease(dimension, delta);
    }

    private BalanceChange change(LockedBalance existing, Dimension dimension, BigDecimal delta) {
        if (existing == null || !existing.warehouseId().equals(dimension.warehouseId())
                || mysql.changeQuantity(existing.id(), delta) != 1) {
            return null;
        }
        return new BalanceChange(existing.id(), existing.quantity(), mysql.quantity(existing.id()));
    }
}
