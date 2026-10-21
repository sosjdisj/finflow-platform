package org.example.finflowbackend.common.bootstrap;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * dev 环境启动自检：验证 Redis / Kafka 连通（MySQL 由 Flyway 迁移成功即验证）。
 * 仅 dev profile 生效；连接失败直接中断启动，尽早暴露环境问题。
 */
@Component
@Profile("dev")
public class DevInfrastructureCheckRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevInfrastructureCheckRunner.class);

    private final RedisConnectionFactory redisConnectionFactory;
    private final String kafkaBootstrapServers;

    public DevInfrastructureCheckRunner(RedisConnectionFactory redisConnectionFactory,
                                        @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var connection = redisConnectionFactory.getConnection()) {
            Object pong = connection.execute("PING");
            log.info("Redis 连通: {}", pong);
        }
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers))) {
            int nodeCount = admin.describeCluster().nodes().get(10, TimeUnit.SECONDS).size();
            log.info("Kafka 连通: {} ({} 个节点)", kafkaBootstrapServers, nodeCount);
        }
    }
}
