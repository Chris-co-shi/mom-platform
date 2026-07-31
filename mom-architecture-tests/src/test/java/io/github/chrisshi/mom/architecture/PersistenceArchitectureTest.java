package io.github.chrisshi.mom.architecture;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemApplicationEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemCatalogReleaseEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nReleaseEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemNavigationItemEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserPreferenceEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserViewSettingEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemApplicationRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemDictionaryRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemNavigationRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.repository.MybatisSystemParameterRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** MOM 持久化类型位置、Repository 抽象、技术栈与 Entity 能力门禁。 */
class PersistenceArchitectureTest {
    private static final String[] BOUNDED_CONTEXT_PACKAGES = {
            "io.github.chrisshi.mom.iam..", "io.github.chrisshi.mom.mdm..",
            "io.github.chrisshi.mom.integration..", "io.github.chrisshi.mom.system..",
            "io.github.chrisshi.mom.mes..", "io.github.chrisshi.mom.wms..",
            "io.github.chrisshi.mom.qms..", "io.github.chrisshi.mom.ems..",
            "io.github.chrisshi.mom.eam..", "io.github.chrisshi.mom.traceability.."
    };

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.chrisshi.mom");

    @Test
    void businessMappersMustStayInPersistenceAndUseMomBaseMapper() {
        classes().that().haveSimpleNameEndingWith("Mapper")
                .and().doNotHaveFullyQualifiedName(MomBaseMapper.class.getName())
                .should().resideInAnyPackage("..infrastructure.persistence..")
                .andShould().beAssignableTo(MomBaseMapper.class)
                .check(productionClasses);
    }

    @Test
    void databaseEntitiesMustStayInPersistencePackages() {
        classes().that().haveSimpleNameEndingWith("Entity")
                .and().resideOutsideOfPackage("io.github.chrisshi.mom.data.entity..")
                .should().resideInAnyPackage("..infrastructure.persistence..")
                .check(productionClasses);
    }

    @Test
    void systemEntitiesMustSelectBaseClassByCapability() throws NoSuchFieldException {
        classes()
                .that().resideInAnyPackage("io.github.chrisshi.mom.system.infrastructure.persistence..")
                .and().haveSimpleNameEndingWith("Entity")
                .and().doNotHaveFullyQualifiedName(SystemI18nReleaseEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemUserPreferenceEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemUserViewSettingEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemApplicationEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemNavigationItemEntity.class.getName())
                .and().doNotHaveFullyQualifiedName(SystemCatalogReleaseEntity.class.getName())
                .should().beAssignableTo(BaseEntity.class)
                .check(productionClasses);
        assertAuditOnly(SystemI18nReleaseEntity.class);
        assertAuditOnly(SystemCatalogReleaseEntity.class);
        assertAuditVersionedWithoutLogicalDelete(SystemUserPreferenceEntity.class);
        assertAuditVersionedWithoutLogicalDelete(SystemUserViewSettingEntity.class);
        assertAuditVersionedWithoutLogicalDelete(SystemApplicationEntity.class);
        assertAuditVersionedWithoutLogicalDelete(SystemNavigationItemEntity.class);
    }

    private static void assertAuditOnly(Class<?> type) {
        assertThat(BaseAuditEntity.class.isAssignableFrom(type)).isTrue();
        assertThat(BaseEntity.class.isAssignableFrom(type)).isFalse();
        assertThat(java.util.Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> field.getAnnotation(Version.class) != null)).isFalse();
        assertThat(java.util.Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> field.getAnnotation(TableLogic.class) != null)).isFalse();
    }

    private static void assertAuditVersionedWithoutLogicalDelete(Class<?> type)
            throws NoSuchFieldException {
        assertThat(BaseAuditEntity.class.isAssignableFrom(type)).isTrue();
        assertThat(BaseEntity.class.isAssignableFrom(type)).isFalse();
        assertThat(type.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(java.util.Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> field.getAnnotation(TableLogic.class) != null)).isFalse();
    }

    @Test
    void boundedContextsMustNotUseMybatisPlusGenericServices() {
        noClasses().that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.spring.service..",
                        "com.baomidou.mybatisplus.extension.service..")
                .check(productionClasses);
    }

    @Test
    void mybatisPlusRepositoriesMustStayInsideInfrastructureRepository() {
        noClasses().that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .and().resideOutsideOfPackage("..infrastructure.persistence.repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.baomidou.mybatisplus.spring.repository..",
                        "com.baomidou.mybatisplus.extension.repository..")
                .check(productionClasses);
    }

    @Test
    void domainRepositoryPortsMustRemainFrameworkIndependent() {
        noClasses().that().resideInAnyPackage("..domain..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat().resideInAnyPackage("com.baomidou.mybatisplus..")
                .check(productionClasses);
    }

    @Test
    void upperLayersMustNotDependOnConcreteMybatisRepositories() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..", "..web..", "..interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence.repository..")
                .check(productionClasses);
    }

    @Test
    void approvedSingleTableAdaptersMustUseCrudRepository() {
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemParameterRepository.class)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemDictionaryRepository.class)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemDictionaryItemRepository.class)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemApplicationRepository.class)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(MybatisSystemNavigationRepository.class)).isTrue();
    }

    @Test
    void boundedContextsMustNotIntroduceDirectJdbcAccess() {
        noClasses().that().resideInAnyPackage(BOUNDED_CONTEXT_PACKAGES)
                .and().doNotHaveFullyQualifiedName(
                        "io.github.chrisshi.mom.iam.security.IamAuthorizationServerProtocolConfiguration")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.jdbc..", "java.sql..")
                .check(productionClasses);
    }

    @Test
    void persistenceEntitiesMustNotUseLocalDateTime() {
        noClasses().that().resideInAnyPackage("..infrastructure.persistence..")
                .and().haveSimpleNameEndingWith("Entity")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.time.LocalDateTime")
                .check(productionClasses);
    }

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
