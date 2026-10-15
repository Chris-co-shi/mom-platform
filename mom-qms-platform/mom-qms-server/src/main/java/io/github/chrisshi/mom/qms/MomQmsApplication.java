package io.github.chrisshi.mom.qms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** QMS 执行域服务入口；质量基础定义由 MDM 持有。 */
@SpringBootApplication
public class MomQmsApplication {
    /** 启动 QMS 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(MomQmsApplication.class, args);
    }
}
