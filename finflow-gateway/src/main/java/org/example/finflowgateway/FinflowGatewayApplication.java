package org.example.finflowgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 开放平台网关：M01 只验证版本与 Nacos / Sentinel 连通，M13 起承载商户 /open/v1/** 路由与边缘准入。
 * 后台 /api/v1/** 不经过本进程，商户请求只从 8091 进入。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FinflowGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinflowGatewayApplication.class, args);
	}

}
