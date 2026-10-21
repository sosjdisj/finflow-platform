package org.example.finflowbackend.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类（S39）：单例 MySQL 8.0 Testcontainer 供所有 it 测试共享，
 * Flyway 在容器库内自动建表；Redis/Kafka/MinIO 继续走本地 compose。
 * 渠道对接指向测试内 HTTP 桩（{@link FakeChannelStub}）：随机端口 + 每轮注入通知地址。
 * 镜像走华为云同步源（docker.io 国内拉取受阻），ryuk 已禁用（见 testcontainers.properties），
 * 容器随 JVM 退出时由 shutdown hook 清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class IntegrationTestBase {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/mysql:8.0")
                    .asCompatibleSubstituteFor("mysql"))
            .withUrlParam("serverTimezone", "Asia/Shanghai")
            .withUrlParam("useSSL", "false")
            .withUrlParam("allowPublicKeyRetrieval", "true");

    static {
        MYSQL.start();
        Runtime.getRuntime().addShutdownHook(new Thread(MYSQL::stop));
    }

    /** 内嵌容器端口：通道桩据此把异步通知推回后端。 */
    @Value("${local.server.port}")
    protected int serverPort;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("finflow.remote-channel.base-url", () -> FakeChannelStub.get().baseUrl());
    }

    /** 每轮重置渠道桩旋钮并指向当前后端端口（子类 @BeforeEach 在此之后按需覆盖失败率）。 */
    @BeforeEach
    void resetChannelStub() {
        FakeChannelStub stub = FakeChannelStub.get();
        stub.reset();
        stub.setNotifyBaseUrl("http://127.0.0.1:" + serverPort + "/api/v1/channels");
    }
}
