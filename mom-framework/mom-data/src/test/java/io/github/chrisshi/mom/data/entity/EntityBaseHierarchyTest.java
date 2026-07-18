package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据实体基类层次契约测试。
 *
 * <p>该测试防止后续维护把审计、乐观锁和逻辑删除字段重新堆回最小主键基类，确保中间表、日志表和普通
 * 业务表可以按实际持久化语义选择不同继承层次。同时锁定 String 主键与 MyBatis-Plus 分布式 ID 策略，
 * 避免未来回退到会在前端产生精度风险的 Long 对外契约。</p>
 */
class EntityBaseHierarchyTest {

    /**
     * 验证三层实体能力按主键、审计和完整可更新语义逐级扩展。
     */
    @Test
    void hierarchyShouldKeepOptionalCapabilitiesSeparated() throws NoSuchFieldException {
        assertTrue(BaseIdEntity.class.isAssignableFrom(BaseAuditEntity.class));
        assertTrue(BaseAuditEntity.class.isAssignableFrom(BaseEntity.class));

        assertFalse(declaresField(BaseIdEntity.class, "createdAt"));
        assertFalse(declaresField(BaseIdEntity.class, "version"));
        assertFalse(declaresField(BaseIdEntity.class, "deleted"));
        assertFalse(declaresField(BaseAuditEntity.class, "version"));
        assertFalse(declaresField(BaseAuditEntity.class, "deleted"));

        Field idField = BaseIdEntity.class.getDeclaredField("id");
        assertEquals(String.class, idField.getType());
        assertEquals(IdType.ASSIGN_ID, idField.getAnnotation(TableId.class).type());

        Field deletedField = BaseEntity.class.getDeclaredField("deleted");
        assertEquals(Boolean.class, deletedField.getType());
        TableLogic tableLogic = deletedField.getAnnotation(TableLogic.class);
        assertEquals("false", tableLogic.value());
        assertEquals("true", tableLogic.delval());
    }

    /**
     * 判断字段是否由指定类型直接声明，不把父类字段误判为当前层新增能力。
     *
     * @param type 待检查类型
     * @param fieldName 字段名
     * @return 当前类型直接声明该字段时返回 true
     */
    private static boolean declaresField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
