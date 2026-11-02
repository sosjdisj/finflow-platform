package org.example.finflowgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 骨架冒烟测试：不连 Nacos、不忘控制台上报，只验证网关自身的自动装配能起来。
 * 真实注册验证在 M01 的人工验收里跑（Nacos 控制台可见 finflow-gateway）。
 */
@SpringBootTest(properties = {
		"spring.cloud.nacos.discovery.enabled=false",
		"spring.cloud.sentinel.eager=false"
})
class FinflowGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
