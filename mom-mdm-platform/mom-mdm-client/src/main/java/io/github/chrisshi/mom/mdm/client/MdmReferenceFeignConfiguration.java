package io.github.chrisshi.mom.mdm.client;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * MDM 同步只读调用的有限超时；不启用自动重试或伪成功降级。
 */
public class MdmReferenceFeignConfiguration {
    /**
     * 连接上限 2 秒、读取上限 3 秒，避免调用方事务被无限期占用。
     */
    @Bean
    public Request.Options mdmReferenceRequestOptions() {
        return new Request.Options(Duration.ofSeconds(2), Duration.ofSeconds(3), true);
    }
}
