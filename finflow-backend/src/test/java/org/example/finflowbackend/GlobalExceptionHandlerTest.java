package org.example.finflowbackend;

import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.support.MockMapperConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验收：人为抛异常返回统一错误结构（业务异常 / 参数校验失败 / 未知异常 / 路径不存在）。
 * 本测试只关心异常处理，与鉴权无关，请求统一注入已认证主体以通过安全过滤链。
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({GlobalExceptionHandlerTest.ExceptionDemoController.class, MockMapperConfig.class})
class GlobalExceptionHandlerTest {

    /** 仅用于通过安全过滤链的已认证主体，不参与断言。 */
    private static final Authentication TEST_AUTH = new UsernamePasswordAuthenticationToken(
            "test-user", null, java.util.List.of());

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessExceptionReturnsUnifiedError() throws Exception {
        mockMvc.perform(get("/test/business").with(SecurityMockMvcRequestPostProcessors.authentication(TEST_AUTH)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()))
                .andExpect(jsonPath("$.message").value("测试业务异常"))
                .andExpect(jsonPath("$.data").value(isEmptyOrNullString()));
    }

    @Test
    void paramValidationErrorReturnsParamError() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(TEST_AUTH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void unknownExceptionReturnsSystemError() throws Exception {
        mockMvc.perform(get("/test/system").with(SecurityMockMvcRequestPostProcessors.authentication(TEST_AUTH)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SYSTEM_ERROR.getMessage()));
    }

    @Test
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/test/not-exist").with(SecurityMockMvcRequestPostProcessors.authentication(TEST_AUTH)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    /** 仅供本测试使用的异常触发端点，不进入主代码。 */
    @RestController
    static class ExceptionDemoController {

        @GetMapping("/test/business")
        public ApiResponse<Void> business() {
            throw new BusinessException(ErrorCode.CONFLICT, "测试业务异常");
        }

        @PostMapping("/test/validate")
        public ApiResponse<Void> validate(@jakarta.validation.Valid @RequestBody DemoRequest request) {
            return ApiResponse.ok();
        }

        @GetMapping("/test/system")
        public ApiResponse<Void> system() {
            throw new IllegalStateException("boom");
        }

        record DemoRequest(@jakarta.validation.constraints.NotBlank String name) {
        }
    }
}
