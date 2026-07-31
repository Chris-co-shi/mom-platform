package io.github.chrisshi.mom.mdm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomMdmApplicationTest {

    @Test
    void applicationShouldRemainAProbeFreeBoundedContextEntryPoint() {
        assertNotNull(MomMdmApplication.class.getAnnotation(SpringBootApplication.class));
        assertTrue(Arrays.stream(MomMdmApplication.class.getAnnotations())
                .noneMatch(annotation -> annotation.annotationType().getName()
                        .equals("org.springframework.cloud.openfeign.EnableFeignClients")));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.chrisshi.mom.mdm.application.MdmDataProbeService"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.chrisshi.mom.mdm.application.MdmOutboxProbeService"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.chrisshi.mom.mdm.application.MdmSeataAtProbeService"));
    }
}
