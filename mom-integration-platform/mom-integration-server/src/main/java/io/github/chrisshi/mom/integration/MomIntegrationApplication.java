package io.github.chrisshi.mom.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MOM Integration 服务启动入口；不注册 MDM、Messaging 或 Seata Phase 01 技术探针。 */
@SpringBootApplication
public class MomIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MomIntegrationApplication.class, args);
    }
}
