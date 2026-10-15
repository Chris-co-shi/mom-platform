package io.github.chrisshi.mom.qms.application;

import io.github.chrisshi.mom.mdm.api.ManufacturingResourceReference;
import io.github.chrisshi.mom.mdm.api.MaterialReference;
import io.github.chrisshi.mom.mdm.api.UomReference;
import io.github.chrisshi.mom.mdm.client.MdmReferenceClient;
import io.github.chrisshi.mom.qms.application.model.QualityModels.MdmSnapshot;
import io.github.chrisshi.mom.qms.application.model.QualityModels.UomSnapshot;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * QMS 到 MDM 的同步只读适配器。
 *
 * <p>强制在本地事务外执行，避免持有 QMS 数据库锁等待网络；Feign 的 401/403/404/409/503/504
 * 原样抛到 Controller Advice，绝不降级成可用引用。</p>
 */
@Component
public class MdmQualityReferenceGateway {
    private final MdmReferenceClient client;
    public MdmQualityReferenceGateway(MdmReferenceClient client) { this.client = client; }

    /** 校验工厂和物料当前可新引用，并返回需要持久化的版本快照。 */
    @Transactional(propagation = Propagation.NEVER)
    public MdmSnapshot requireSpecificationScope(String plantId, String materialId) {
        ManufacturingResourceReference plant = data(client.getManufacturingResourceReference("PLANT", QmsRules.id(plantId)));
        MaterialReference material = data(client.getMaterialReference(QmsRules.id(materialId)));
        if (!plant.availableForNewUse() || !material.availableForNewUse()
                || plant.version() == null || material.revisionNo() == null || material.version() == null) {
            throw QmsException.disabledReference();
        }
        return new MdmSnapshot(plant.id(), plant.version(), material.id(), material.revisionNo(), material.version());
    }

    /** 校验数值特性使用的单位当前可用，并返回量纲和技术版本。 */
    @Transactional(propagation = Propagation.NEVER)
    public UomSnapshot requireUom(String uomId) {
        UomReference uom = data(client.getUomReference(QmsRules.id(uomId)));
        if (!uom.availableForNewUse() || uom.version() == null || uom.dimensionId() == null) {
            throw QmsException.disabledReference();
        }
        return new UomSnapshot(uom.id(), uom.version(), uom.dimensionId());
    }

    private static <T> T data(Result<T> result) {
        if (result == null || !Result.SUCCESS_CODE.equals(result.code()) || result.data() == null) {
            throw QmsException.invalidReference();
        }
        return result.data();
    }
}
