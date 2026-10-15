package io.github.chrisshi.mom.mdm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定 MDM 资源根和状态命令的动作优先路径。 */
class MdmRouteContractTest {
    @Test
    void materialRoutesMustUseResourceRootAndPlaceCommandBeforeIdentifier() throws Exception {
        assertThat(MaterialController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/materials");
        assertThat(MaterialController.class.getMethod("enableMaterial", String.class, MaterialController.VersionRequest.class)
                .getAnnotation(PatchMapping.class).value()).containsExactly("/enable/{id}");
        assertThat(MaterialController.class.getMethod("disableMaterial", String.class, MaterialController.VersionRequest.class)
                .getAnnotation(PatchMapping.class).value()).containsExactly("/disable/{id}");
    }

    @Test
    void governanceCheckMustBeReadOnlyOnDemandPostWithoutApiPrefix() throws Exception {
        assertThat(MasterDataGovernanceController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/governance");
        assertThat(MasterDataGovernanceController.class.getMethod("check", MasterDataGovernanceController.CheckRequest.class)
                .getAnnotation(PostMapping.class).value()).containsExactly("/consistency-checks");
    }
}
