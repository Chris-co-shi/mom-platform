package io.github.chrisshi.mom.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "io.github.chrisshi.mom.mdm.client")
public class MomWmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MomWmsApplication.class, args);
    }
}
