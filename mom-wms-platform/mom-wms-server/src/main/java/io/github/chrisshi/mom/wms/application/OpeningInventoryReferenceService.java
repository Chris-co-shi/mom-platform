package io.github.chrisshi.mom.wms.application;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.mdm.api.LocationReference;
import io.github.chrisshi.mom.mdm.api.MaterialReference;
import io.github.chrisshi.mom.mdm.api.UomReference;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.mdm.client.MdmReferenceClient;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 期初业务使用的只读 MDM 权威引用解析器，属于 WMS Application。
 * <p>负责确认静态主数据有效并生成快照；不写 MDM，也不负责期初单生命周期。所有网络调用禁止处于 WMS 数据库事务。</p>
 */
@Service
@RequiredArgsConstructor
public class OpeningInventoryReferenceService {
    private final MdmReferenceClient mdm;

    

    /**
     * 验证仓库当前可供新业务引用并返回可信编码快照。
     * @param id MDM 仓库稳定 ID @return 经 MDM 验证的静态快照 @throws IllegalArgumentException 仓库当前不可引用时抛出
     */
    @Transactional(propagation = Propagation.NEVER)
    public WarehouseResourceReference warehouse(String id) {
        WarehouseResourceReference value = data(mdm.getWarehouseReference(id));
        if (value == null || !"WAREHOUSE".equals(value.resourceType()) || !value.availableForNewUse())
            throw new OpeningInventoryReferenceException(value == null ? null : value.invalidReason());
        return value;
    }

    /**
     * 验证物料、批次策略及其库存基础单位，并返回可信展示快照。
     * @param id MDM 物料稳定 ID @return 物料与库存单位快照 @throws IllegalArgumentException 物料或单位不可引用时抛出
     */
    @Transactional(propagation = Propagation.NEVER)
    public MaterialSnapshot material(String id) {
        MaterialReference material = data(mdm.getMaterialReference(id));
        require(material != null && material.availableForNewUse());
        UomReference uom = data(mdm.getUomReference(material.baseUomId()));
        require(uom != null && uom.availableForNewUse());
        return new MaterialSnapshot(material.id(), material.code(), material.name(), material.batchTrackingPolicy(), uom.code());
    }

    /**
     * 验证库位以及其所属仓储区域父链与指定仓库一致。
     * @param id MDM 库位稳定 ID @param warehouseId 期初单目标仓库 ID @return 经验证的库位展示快照
     * @throws IllegalArgumentException 库位无效或不属于目标仓库时抛出
     */
    @Transactional(propagation = Propagation.NEVER)
    public LocationSnapshot location(String id, String warehouseId) {
        LocationReference location = data(mdm.getLocationReference(id));
        require(location != null && location.availableForNewUse() && location.warehouseAreaId() != null);
        WarehouseResourceReference area = data(mdm.getWarehouseAreaReference(location.warehouseAreaId()));
        require(area != null && area.availableForNewUse() && warehouseId.equals(area.parentId()));
        return new LocationSnapshot(location.id(), location.code());
    }

    private static <T> T data(Result<T> result) {
        if (result == null || !Result.SUCCESS_CODE.equals(result.code())) throw new IllegalStateException("MDM 引用服务暂不可用");
        return result.data();
    }

    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("MDM 主数据无效或库位不属于目标仓库");
    }

    /**
     * 物料与其当前库存单位的可信快照。
     * @param id 物料 ID @param code 物料编码 @param name 当前物料名称 @param batchPolicy 当前批次策略 @param uomCode 库存单位编码
     */
    public record MaterialSnapshot(String id, String code, String name, String batchPolicy, String uomCode) { }
    /** 库位可信展示快照。 @param id 库位 ID @param code 当前库位编码 */
    public record LocationSnapshot(String id, String code) { }
}
