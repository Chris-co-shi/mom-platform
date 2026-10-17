package io.github.chrisshi.mom.mdm.codesegment;

import io.github.chrisshi.mom.coderule.field.CodeFieldMetadata;
import io.github.chrisshi.mom.coderule.field.CodeFieldProvider;
import io.github.chrisshi.mom.coderule.field.CodeGenerationContext;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialCategoryMapper;
import org.springframework.stereotype.Component;
import java.util.List;

/** MDM 真实物料分类编码字段。 */
@Component
public final class MaterialCategoryCodeFieldProvider implements CodeFieldProvider {
    private final MaterialCategoryMapper categories;
    public MaterialCategoryCodeFieldProvider(MaterialCategoryMapper categories) { this.categories = categories; }
    public CodeFieldMetadata metadata() {
        return new CodeFieldMetadata("mdm.materialCategory.code", "物料分类编码", "当前物料分类的业务编码", List.of("materialCategoryId"));
    }
    public String resolve(CodeGenerationContext context) {
        MaterialCategoryEntity category = categories.selectById(context.require("materialCategoryId"));
        if (category == null || !"ENABLED".equals(category.getStatus()))
            throw new CodeSegmentException("物料分类不存在或已停用");
        return category.getCode();
    }
}
