package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.system.controller.dictionary.DictionaryTypeController;
import io.github.chrisshi.mom.system.controller.dictionary.DictionaryTypeController.StatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证字典类型状态管理路径与其余字典管理接口保持同一前缀。
 */
class DictionaryControllerMappingTest {

    @Test
    void typeStatusMustUseOperationBeforeIdAndItsOwnResourceRoot() throws NoSuchMethodException {
        PatchMapping mapping = DictionaryTypeController.class
                .getMethod("changeStatus", String.class, StatusRequest.class)
                .getAnnotation(PatchMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/change-status/{id}");
        assertThat(DictionaryTypeController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/dictionary-types");
    }
}
