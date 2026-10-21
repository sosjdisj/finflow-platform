package org.example.finflowbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FinFlow 支付平台启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableKafka
public class FinflowBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinflowBackendApplication.class, args);
    }

}
