package io.github.chrisshi.mom.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomIntegrationApplicationTest {

    @Test
    void applicationShouldNotRegisterRetiredCrossServiceProbeClients() {
        assertNotNull(MomIntegrationApplication.class.getAnnotation(SpringBootApplication.class));
        assertTrue(Arrays.stream(MomIntegrationApplication.class.getAnnotations())
                .noneMatch(annotation -> annotation.annotationType().getName()
                        .equals("org.springframework.cloud.openfeign.EnableFeignClients")));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.chrisshi.mom.integration.interfaces.rest.IntegrationMdmProbeController"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.github.chrisshi.mom.integration.application.IntegrationSeataAtParticipantService"));
    }
}
