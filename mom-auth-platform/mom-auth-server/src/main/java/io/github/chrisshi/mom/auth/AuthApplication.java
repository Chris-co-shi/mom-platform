package io.github.chrisshi.mom.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author 史偕成
 * @date 2026/09/02 14:14
 **/
@EnableDiscoveryClient
@SpringBootApplication
public class AuthApplication {
    static void main() {
        SpringApplication.run(AuthApplication.class);
    }
}
