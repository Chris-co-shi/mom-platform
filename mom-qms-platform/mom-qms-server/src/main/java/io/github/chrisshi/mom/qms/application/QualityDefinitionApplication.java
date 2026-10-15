package io.github.chrisshi.mom.qms.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.qms.application.model.QmsPageParams.*;
import io.github.chrisshi.mom.qms.application.model.QualityModels;
import io.github.chrisshi.mom.qms.infrastructure.entity.*;
import io.github.chrisshi.mom.qms.infrastructure.mapper.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 质量定义用例编排器。
 *
 * <p>所有 MDM 调用在本类的非事务方法中完成，随后委托 {@link QualityDefinitionWriter} 开启 QMS 本地事务。
 * 这不是跨服务事务：远端校验成功但本地提交失败时不会写 MDM，也不会产生部分业务事实。</p>
 */
@Component
public class QualityDefinitionApplication {
    private final MdmQualityReferenceGateway mdm;
    private final QualityDefinitionWriter writer;
    private final InspectionCharacteristicMapper characteristics;
    private final InspectionMethodMapper methods;
    private final QualitySpecificationMapper specifications;
    private final SpecificationRevisionMapper revisions;
    private final PageAdapter pages;

    public QualityDefinitionApplication(MdmQualityReferenceGateway mdm, QualityDefinitionWriter writer,
            InspectionCharacteristicMapper characteristics, InspectionMethodMapper methods,
            QualitySpecificationMapper specifications, SpecificationRevisionMapper revisions, PageAdapter pages) {
        this.mdm = mdm; this.writer = writer; this.characteristics = characteristics; this.methods = methods;
        this.specifications = specifications; this.revisions = revisions; this.pages = pages;
    }

    /** 在事务外校验 UOM 后创建特性。 */
    public QualityModels.CharacteristicView createCharacteristic(String code, String name, String uomId) {
        return writer.createCharacteristic(code, name, mdm.requireUom(uomId));
    }
    /** 在事务外重新校验 UOM 后更新特性。 */
    public QualityModels.CharacteristicView updateCharacteristic(String id, String name, String uomId,
            Boolean enabled, Long version) {
        return writer.updateCharacteristic(id, name, enabled, version, mdm.requireUom(uomId));
    }
    /** 创建本地检验方法。 */
    public QualityModels.MethodView createMethod(String code, String name, String description) {
        return writer.createMethod(code, name, description);
    }
    /** 更新本地检验方法。 */
    public QualityModels.MethodView updateMethod(String id, String name, String description,
            Boolean enabled, Long version) {
        return writer.updateMethod(id, name, description, enabled, version);
    }
    /** 校验 MDM 范围和全部条目后创建第一草稿。 */
    public QualityModels.DetailView createSpecification(String code, String name, String plantId, String materialId,
            List<QualityModels.ItemInput> inputs) {
        QualityModels.MdmSnapshot scope = mdm.requireSpecificationScope(plantId, materialId);
        return writer.createSpecification(code, name, scope, prepare(inputs));
    }
    /** 替换草稿条目；远端单位校验不持有 QMS 事务。 */
    public QualityModels.DetailView updateDraft(String revisionId, Long lockVersion,
            List<QualityModels.ItemInput> inputs) {
        return writer.updateDraft(revisionId, lockVersion, prepare(inputs));
    }
    /** 复制最后一个不可变修订为下一草稿并重新校验引用。 */
    public QualityModels.DetailView nextRevision(String specificationId) {
        QualityModels.DetailView current = writer.detailBySpecification(specificationId);
        return writer.nextRevision(specificationId, prepare(toInputs(current.items())));
    }
    /** 发布前重新校验工厂、物料、单位和本地特性/方法。 */
    public QualityModels.DetailView release(String revisionId, Long lockVersion) {
        QualityModels.DetailView detail = writer.detailByRevision(revisionId);
        QualityModels.MdmSnapshot scope = mdm.requireSpecificationScope(detail.plantId(), detail.materialId());
        return writer.release(revisionId, lockVersion, scope, prepare(toInputs(detail.items())));
    }
    /** 废止只修改 QMS 本地状态，不修改历史快照。 */
    public QualityModels.DetailView obsolete(String revisionId, Long lockVersion) {
        return writer.obsolete(revisionId, lockVersion);
    }
    public QualityModels.DetailView detail(String specificationId) { return writer.detailBySpecification(specificationId); }
    public QualityModels.DetailView revision(String revisionId) { return writer.detailByRevision(revisionId); }

    /** 按启用状态分页查询数值检验特性。 */
    @Transactional(readOnly = true)
    public PageResult<QualityModels.CharacteristicView> characteristics(PageQuery<CharacteristicParams> query) {
        Page<InspectionCharacteristicEntity> page = pages.toPage(query);
        LambdaQueryWrapper<InspectionCharacteristicEntity> conditions = new LambdaQueryWrapper<>();
        if (query.params().enabled() != null) conditions.eq(InspectionCharacteristicEntity::getEnabled, query.params().enabled());
        conditions.orderByAsc(InspectionCharacteristicEntity::getCode).orderByAsc(InspectionCharacteristicEntity::getId);
        characteristics.selectPage(page, conditions);
        return pages.toResult(page, entity -> new QualityModels.CharacteristicView(entity.getId(), entity.getCode(),
                entity.getName(), entity.getDefaultUomId(), entity.getDefaultUomVersion(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion()));
    }

    /** 按启用状态分页查询检验方法。 */
    @Transactional(readOnly = true)
    public PageResult<QualityModels.MethodView> methods(PageQuery<MethodParams> query) {
        Page<InspectionMethodEntity> page = pages.toPage(query);
        LambdaQueryWrapper<InspectionMethodEntity> conditions = new LambdaQueryWrapper<>();
        if (query.params().enabled() != null) conditions.eq(InspectionMethodEntity::getEnabled, query.params().enabled());
        conditions.orderByAsc(InspectionMethodEntity::getCode).orderByAsc(InspectionMethodEntity::getId);
        methods.selectPage(page, conditions);
        return pages.toResult(page, entity -> new QualityModels.MethodView(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription(), Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion()));
    }

    /** 按工厂和物料分页查询质量标准身份及最新修订状态。 */
    @Transactional(readOnly = true)
    public PageResult<QualityModels.SpecificationView> specifications(PageQuery<SpecificationParams> query) {
        Page<QualitySpecificationEntity> page = pages.toPage(query);
        LambdaQueryWrapper<QualitySpecificationEntity> conditions = new LambdaQueryWrapper<>();
        if (query.params().plantId() != null && !query.params().plantId().isBlank())
            conditions.eq(QualitySpecificationEntity::getPlantId, QmsRules.id(query.params().plantId()));
        if (query.params().materialId() != null && !query.params().materialId().isBlank())
            conditions.eq(QualitySpecificationEntity::getMaterialId, QmsRules.id(query.params().materialId()));
        conditions.orderByAsc(QualitySpecificationEntity::getCode).orderByAsc(QualitySpecificationEntity::getId);
        specifications.selectPage(page, conditions);
        return pages.toResult(page, entity -> {
            SpecificationRevisionEntity latest = revisions.selectOne(new LambdaQueryWrapper<SpecificationRevisionEntity>()
                    .eq(SpecificationRevisionEntity::getSpecificationId, entity.getId())
                    .orderByDesc(SpecificationRevisionEntity::getRevisionNo).last("LIMIT 1"));
            return new QualityModels.SpecificationView(entity.getId(), entity.getCode(), entity.getName(),
                    entity.getPlantId(), entity.getMaterialId(), entity.getInspectionUsage(),
                    latest == null ? 0 : latest.getRevisionNo(), latest == null ? null : latest.getStatus());
        });
    }

    private List<QualityModels.PreparedItem> prepare(List<QualityModels.ItemInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 200) throw QmsException.invalidReference();
        Set<Integer> sequences = new HashSet<>();
        List<QualityModels.PreparedItem> prepared = new ArrayList<>();
        for (QualityModels.ItemInput input : inputs) {
            if (input == null || input.sequenceNo() == null || input.sequenceNo() <= 0
                    || !sequences.add(input.sequenceNo())) throw QmsException.invalidReference();
            QualityModels.LocalItemReference local = writer.localReference(input.characteristicId(), input.methodId());
            QualityModels.UomSnapshot defaultUom = mdm.requireUom(local.defaultUomId());
            QualityModels.UomSnapshot itemUom = mdm.requireUom(input.uomId());
            if (!defaultUom.dimensionId().equals(itemUom.dimensionId())) throw QmsException.invalidReference();
            validateRange(input.lowerLimit(), input.targetValue(), input.upperLimit());
            prepared.add(new QualityModels.PreparedItem(input.sequenceNo(), local.characteristicId(),
                    local.characteristicVersion(), local.methodId(), local.methodVersion(), itemUom.id(), itemUom.version(),
                    input.lowerLimit(), input.targetValue(), input.upperLimit(), !Boolean.FALSE.equals(input.mandatory())));
        }
        return prepared;
    }

    private static void validateRange(String lower, String target, String upper) {
        BigDecimal low = QmsRules.optionalDecimal(lower), goal = QmsRules.optionalDecimal(target), high = QmsRules.optionalDecimal(upper);
        if (low == null && goal == null && high == null) throw QmsException.invalidRange();
        if ((low != null && goal != null && low.compareTo(goal) > 0)
                || (goal != null && high != null && goal.compareTo(high) > 0)
                || (low != null && high != null && low.compareTo(high) > 0)) throw QmsException.invalidRange();
    }
    private static List<QualityModels.ItemInput> toInputs(List<QualityModels.ItemView> items) {
        return items.stream().map(item -> new QualityModels.ItemInput(item.sequenceNo(), item.characteristicId(),
                item.methodId(), item.uomId(), item.lowerLimit(), item.targetValue(), item.upperLimit(), item.mandatory())).toList();
    }
}
