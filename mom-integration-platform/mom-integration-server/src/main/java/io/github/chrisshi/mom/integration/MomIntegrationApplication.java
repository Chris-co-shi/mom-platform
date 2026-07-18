package io.github.chrisshi.mom.integration;

import io.github.chrisshi.mom.mdm.client.MdmServiceProbeClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = MdmServiceProbeClient.class)
public class MomIntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(MomIntegrationApplication.class, args);
    }
}
