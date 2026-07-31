package io.github.chrisshi.mom.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** MOM 主数据服务启动入口；仅承载正式主数据能力，不注册 Phase 01 技术探针。 */
@SpringBootApplication
public class MomMdmApplication {

    public static void main(String[] args) {
        SpringApplication.run(MomMdmApplication.class, args);
    }
}
