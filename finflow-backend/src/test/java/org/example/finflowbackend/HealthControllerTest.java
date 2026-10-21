package org.example.finflowbackend;

import org.example.finflowbackend.support.MockMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验收：健康检查返回统一 JSON 结构；S40 traceId 贯穿响应头与统一响应体。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(MockMapperConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUnifiedJson() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").isNotEmpty());
    }

    /** S40：无上游 traceId 时自动生成 32 位十六进制，响应头与响应体同源。 */
    @Test
    void healthGeneratesTraceIdWhenMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();
        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(traceId);
        assertTrue(traceId.matches("[0-9a-f]{32}"), "traceId 应为 32 位十六进制: " + traceId);
        assertTrue(result.getResponse().getContentAsString().contains("\"traceId\":\"" + traceId + "\""),
                "响应体 traceId 应与响应头一致");
    }

    /** S40：上游已传 X-Trace-Id 时透传，不重新生成。 */
    @Test
    void healthPropagatesUpstreamTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Trace-Id", "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-abc-123"))
                .andExpect(jsonPath("$.traceId").value("trace-abc-123"));
    }
}
