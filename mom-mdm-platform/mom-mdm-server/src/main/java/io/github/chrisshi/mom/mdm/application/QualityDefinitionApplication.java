package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.model.QualityPageParams.*;
import io.github.chrisshi.mom.mdm.application.model.QualityModels;
import io.github.chrisshi.mom.mdm.api.QualitySpecificationReference;
import io.github.chrisshi.mom.mdm.infrastructure.entity.*;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 质量定义用例编排器。
 *
 * <p>写用例委托 {@link QualityDefinitionWriter} 在 mom_mdm 单一事务中校验引用并持久化；
 * 本类另承担无写副作用的分页查询。</p>
 */
@Component
public class QualityDefinitionApplication {
    private final QualityDefinitionWriter writer;
    private final InspectionCharacteristicMapper characteristics;
    private final InspectionMethodMapper methods;
    private final QualitySpecificationMapper specifications;
    private final SpecificationRevisionMapper revisions;
    private final PageAdapter pages;

    public QualityDefinitionApplication(QualityDefinitionWriter writer,
            InspectionCharacteristicMapper characteristics, InspectionMethodMapper methods,
            QualitySpecificationMapper specifications, SpecificationRevisionMapper revisions, PageAdapter pages) {
        this.writer = writer; this.characteristics = characteristics; this.methods = methods;
        this.specifications = specifications; this.revisions = revisions; this.pages = pages;
    }

    /** 在 MDM 本地事务内校验 UOM 并创建特性。 */
    public QualityModels.CharacteristicView createCharacteristic(String code, String name, String uomId) {
        return writer.createCharacteristic(code, name, uomId);
    }
    /** 在 MDM 本地事务内重新校验 UOM 后更新特性。 */
    public QualityModels.CharacteristicView updateCharacteristic(String id, String name, String uomId,
            Boolean enabled, Long version) {
        return writer.updateCharacteristic(id, name, uomId, enabled, version);
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
    /** 在单一 MDM 事务内校验范围和全部条目后创建第一草稿。 */
    public QualityModels.DetailView createSpecification(String code, String name, String plantId, String materialId,
            List<QualityModels.ItemInput> inputs) {
        return writer.createSpecification(code, name, plantId, materialId, inputs);
    }
    /** 在 MDM 本地事务内替换草稿条目。 */
    public QualityModels.DetailView updateDraft(String revisionId, Long lockVersion,
            List<QualityModels.ItemInput> inputs) {
        return writer.updateDraft(revisionId, lockVersion, inputs);
    }
    /** 复制最后一个不可变修订为下一草稿并重新校验引用。 */
    public QualityModels.DetailView nextRevision(String specificationId) {
        return writer.nextRevision(specificationId);
    }
    /** 发布前在同一本地事务内重新校验工厂、物料、单位、特性和方法。 */
    public QualityModels.DetailView release(String revisionId, Long lockVersion) {
        return writer.release(revisionId, lockVersion);
    }
    /** 废止只修改 MDM 本地状态，不修改历史快照。 */
    public QualityModels.DetailView obsolete(String revisionId, Long lockVersion) {
        return writer.obsolete(revisionId, lockVersion);
    }

    /**
     * 按质量标准身份读取其最新修订详情。
     *
     * @param specificationId 质量标准身份 ID
     * @return 质量标准详情及当前修订条目
     */
    public QualityModels.DetailView detail(String specificationId) { return writer.detailBySpecification(specificationId); }

    /**
     * 按修订 ID 读取质量标准详情。
     *
     * @param revisionId 质量标准修订 ID
     * @return 指定修订详情及条目
     */
    public QualityModels.DetailView revision(String revisionId) { return writer.detailByRevision(revisionId); }

    /** 返回当前已发布质量标准的稳定跨域只读快照。 */
    public QualitySpecificationReference reference(String specificationId) { return writer.reference(specificationId); }

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
            conditions.eq(QualitySpecificationEntity::getPlantId, QualityDefinitionRules.id(query.params().plantId()));
        if (query.params().materialId() != null && !query.params().materialId().isBlank())
            conditions.eq(QualitySpecificationEntity::getMaterialId, QualityDefinitionRules.id(query.params().materialId()));
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

}
