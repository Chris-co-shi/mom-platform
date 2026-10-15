package io.github.chrisshi.mom.qms;

import io.github.chrisshi.mom.mdm.client.MdmReferenceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** QMS 质量定义服务入口；只扫描稳定的 MDM 只读客户端。 */
@SpringBootApplication
@EnableFeignClients(basePackageClasses = MdmReferenceClient.class)
public class MomQmsApplication {
    /** 启动 QMS 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(MomQmsApplication.class, args);
    }
}
