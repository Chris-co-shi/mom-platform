package io.github.chrisshi.mom.mdm.codesegment;

import io.github.chrisshi.mom.core.codesegment.CodeGenerationContext;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentException;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialCategoryMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MDM 字段 Provider 的不存在及停用行为。 */
class MaterialCategoryCodeFieldProviderTest {
    @Test void onlyEnabledCategoryCodeIsResolvedFromTrustedId() {
        var mapper = mock(MaterialCategoryMapper.class);
        var provider = new MaterialCategoryCodeFieldProvider(mapper);
        var context = new CodeGenerationContext(Map.of("materialCategoryId", "42"));
        assertThrows(CodeSegmentException.class, () -> provider.resolve(new CodeGenerationContext(Map.of())));
        assertThrows(CodeSegmentException.class, () -> provider.resolve(context));
        MaterialCategoryEntity category = new MaterialCategoryEntity();
        category.setCode("RM"); category.setStatus("DISABLED");
        when(mapper.selectById("42")).thenReturn(category);
        assertThrows(CodeSegmentException.class, () -> provider.resolve(context));
        category.setStatus("ENABLED");
        assertEquals("RM", provider.resolve(context));
    }
}
