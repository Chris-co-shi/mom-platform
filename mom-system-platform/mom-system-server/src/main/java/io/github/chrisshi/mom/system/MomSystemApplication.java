package io.github.chrisshi.mom.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MOM System Platform 的最小运行时入口。
 *
 * <p>S12 仅建立独立可部署宿主和分层边界，不包含任何参数、字典、偏好、应用目录或菜单能力。该入口不
 * 启用数据源、Redis、消息、Seata、Feign Client 或定时任务；外部发现服务默认关闭，因此空骨架启动
 * 不依赖外部基础设施。后续能力必须由独立 Slice 增加，并继续遵守 ADR-025 的数据所有权边界。</p>
 */
@SpringBootApplication
public class MomSystemApplication {

    /**
     * 启动 System Platform。
     *
     * @param args 命令行配置参数；当前方法不修改参数且无额外副作用
     */
    public static void main(String[] args) {
        SpringApplication.run(MomSystemApplication.class, args);
    }
}
