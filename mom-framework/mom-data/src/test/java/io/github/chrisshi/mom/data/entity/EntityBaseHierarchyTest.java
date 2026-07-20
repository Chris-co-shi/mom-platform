package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据实体主键、创建审计、修改审计和完整业务能力分层契约测试。 */
class EntityBaseHierarchyTest {

    @Test
    void hierarchyShouldKeepOptionalCapabilitiesSeparated() throws NoSuchFieldException {
        assertTrue(BaseIdEntity.class.isAssignableFrom(BaseCreatedEntity.class));
        assertTrue(BaseCreatedEntity.class.isAssignableFrom(BaseAuditEntity.class));
        assertTrue(BaseAuditEntity.class.isAssignableFrom(BaseEntity.class));
        assertFalse(declaresField(BaseIdEntity.class, "createdAt"));
        assertFalse(declaresField(BaseCreatedEntity.class, "updatedAt"));
        assertFalse(declaresField(BaseAuditEntity.class, "version"));
        assertFalse(declaresField(BaseAuditEntity.class, "deleted"));

        Field id = BaseIdEntity.class.getDeclaredField("id");
        assertEquals(String.class, id.getType());
        assertEquals(IdType.ASSIGN_ID, id.getAnnotation(TableId.class).type());
        assertFill(BaseCreatedEntity.class, "createdAt", FieldFill.INSERT);
        assertFill(BaseCreatedEntity.class, "createdBy", FieldFill.INSERT);
        assertFill(BaseAuditEntity.class, "updatedAt", FieldFill.INSERT_UPDATE);
        assertFill(BaseAuditEntity.class, "updatedBy", FieldFill.INSERT_UPDATE);

        Field version = BaseEntity.class.getDeclaredField("version");
        assertNotNull(version.getAnnotation(Version.class));
        assertEquals(0L, new TestEntity().getVersion());
        Field deleted = BaseEntity.class.getDeclaredField("deleted");
        TableLogic tableLogic = deleted.getAnnotation(TableLogic.class);
        assertEquals("false", tableLogic.value());
        assertEquals("true", tableLogic.delval());
        assertFalse(new TestEntity().getDeleted());
    }

    private static void assertFill(Class<?> type, String fieldName, FieldFill expected)
            throws NoSuchFieldException {
        assertEquals(expected, type.getDeclaredField(fieldName).getAnnotation(TableField.class).fill());
    }

    private static boolean declaresField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) { return true; }
        }
        return false;
    }

    private static final class TestEntity extends BaseEntity { }
}
