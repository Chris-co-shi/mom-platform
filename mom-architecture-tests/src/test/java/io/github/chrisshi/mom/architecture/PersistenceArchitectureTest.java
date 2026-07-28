package io.github.chrisshi.mom.architecture;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOM 持久化类型位置与基础 Entity 能力的渐进式架构门禁。
 *
 * <p>本测试分析 Reactor 已编译字节码，不解析 Java import。它只自动判断稳定且可证明的事实：业务 Mapper
 * 和 Entity 的包位置、MyBatis-Plus 通用 Service 禁区、实体时间点类型及公共基类字段类型。Repository
 * 语义、索引有效性和 Entity 应选哪个能力基类仍由逐文件 Review 决定。</p>
 *
 * <p>测试不连接数据库，不改变事务或持久化行为；违反规则时 fail closed，并要求以单类迁移或精确历史
 * 基线处理，禁止扩大为包级排除。</p>
 */
class PersistenceArchitectureTest {

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    /** 业务 Mapper 必须位于 Infrastructure Persistence 并经 MOM 安全基类访问 MyBatis-Plus。 */
    @Test
    void businessMappersMustStayInPersistenceAndUseMomBaseMapper() {
        classes()
                .that().haveSimpleNameEndingWith("Mapper")
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage("..infrastructure.persistence..")
                .andShould().beAssignableTo(MomBaseMapper.class)
                .because("业务 Mapper 属于 Infrastructure Persistence，且 Wrapper-only Update 必须被拒绝")
                .check(productionClasses);
    }

    /** 数据库 Entity 必须位于 Infrastructure Persistence，Framework 能力基类是唯一精确例外。 */
    @Test
    void databaseEntitiesMustStayInPersistencePackages() {
        classes()
                .that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("io.github.chrisshi.mom.data.entity..")
                .should().resideInAnyPackage("..infrastructure.persistence..")
                .because("Entity 是数据库行模型，不能进入 Application、Domain、Web 或公开 API")
                .check(productionClasses);
    }

    /** MOM 业务代码不得把 MyBatis-Plus 通用 Service 当作领域或 Repository 契约。 */
    @Test
    void businessCodeMustNotUseMybatisPlusGenericServices() {
        noClasses()
                .that().resideInAnyPackage("io.github.chrisshi.mom..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.extension.service..")
                .because("IService/ServiceImpl 泄漏 ORM CRUD，不表达 MOM 用例或持久化语义")
                .check(productionClasses);
    }

    /** 数据库时间点使用 Instant，不能用 LocalDateTime 隐去时区语义。 */
    @Test
    void persistenceEntitiesMustNotUseLocalDateTime() {
        noClasses()
                .that().resideInAnyPackage("..infrastructure.persistence..")
                .and().haveSimpleNameEndingWith("Entity")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.time.LocalDateTime")
                .because("全球时间点统一使用 Instant/timestamptz")
                .check(productionClasses);
    }

    /** 公共 Entity 基类必须保持 String ID、Long Version 与 Boolean 逻辑删除能力。 */
    @Test
    void sharedEntityCapabilitiesMustKeepStableTypes() throws NoSuchFieldException {
        Field id = BaseIdEntity.class.getDeclaredField("id");
        Field version = BaseEntity.class.getDeclaredField("version");
        Field deleted = BaseEntity.class.getDeclaredField("deleted");

        assertThat(id.getType()).isEqualTo(String.class);
        assertThat(version.getType()).isEqualTo(Long.class);
        assertThat(version.getAnnotation(Version.class)).isNotNull();
        assertThat(deleted.getType()).isEqualTo(Boolean.class);
        assertThat(deleted.getAnnotation(TableLogic.class)).isNotNull();
    }
}
