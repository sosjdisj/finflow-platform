package org.example.finflowbackend.openapi;

import org.example.finflowbackend.merchant.api.ApiKeyApi;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.merchant.api.MerchantDetailResponse;
import org.example.finflowbackend.merchant.api.MerchantResponse;
import org.example.finflowbackend.openapi.config.OpenApiProperties;
import org.example.finflowbackend.openapi.security.InternalAccessGuard;
import org.example.finflowbackend.openapi.security.OpenApiAuthFilter;
import org.example.finflowbackend.openapi.security.OpenApiCaller;
import org.example.finflowbackend.openapi.security.OpenApiNonceStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M13 网关可信路径单测：带内部令牌 + 商户号的请求跳过签名 / nonce，
 * 但商户状态必须复核；令牌伪造或缺商户号时落回 M12 全量校验。
 */
class OpenApiTrustedAccessTest {

    private static final String TOKEN = "finflow-internal-test-token";

    private final ApiKeyApi apiKeyApi = mock(ApiKeyApi.class);
    private final MerchantApi merchantApi = mock(MerchantApi.class);
    private final OpenApiNonceStore nonceStore = mock(OpenApiNonceStore.class);
    private final InternalAccessGuard guard = new InternalAccessGuard(TOKEN);
    private final OpenApiAuthFilter filter = new OpenApiAuthFilter(apiKeyApi, merchantApi, nonceStore,
            new OpenApiProperties(300, 600), new ObjectMapper(), guard);

    @Test
    void trustedRequestSkipsSignatureAndNonce() throws Exception {
        when(merchantApi.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));
        MockHttpServletRequest request = trustedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        OpenApiCaller caller = (OpenApiCaller) chain.getRequest().getAttribute(OpenApiCaller.ATTRIBUTE);
        assertEquals(1L, caller.merchantId());
        assertEquals("fk_test", caller.apiKey());
        verify(apiKeyApi, never()).findCredential(anyString());
        verify(nonceStore, never()).tryConsume(anyString(), anyString(), anyInt());
    }

    @Test
    void trustedRequestStillRejectsSuspendedMerchant() throws Exception {
        when(merchantApi.getDetail(1L)).thenReturn(merchantDetail("SUSPENDED"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(trustedRequest(), response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(body(response).contains("\"code\":47007"));
        assertNull(chain.getRequest());
    }

    @Test
    void forgedTokenFallsBackToSignatureCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/v1/payments");
        request.addHeader(InternalAccessGuard.HEADER_TOKEN, "forged-token");
        request.addHeader(InternalAccessGuard.HEADER_MERCHANT_ID, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(body(response).contains("\"code\":47002"));
    }

    @Test
    void trustedTokenWithoutMerchantIdFallsBackToSignatureCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/v1/payments");
        request.addHeader(InternalAccessGuard.HEADER_TOKEN, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(body(response).contains("\"code\":47002"));
    }

    private MockHttpServletRequest trustedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/open/v1/payments");
        request.addHeader(InternalAccessGuard.HEADER_TOKEN, TOKEN);
        request.addHeader(InternalAccessGuard.HEADER_MERCHANT_ID, "1");
        request.addHeader("X-Api-Key", "fk_test");
        return request;
    }

    private MerchantDetailResponse merchantDetail(String status) {
        MerchantResponse merchant = new MerchantResponse(1L, "M20261031000001", "网关验收商户", null, "张三",
                "13800000000", null, "91110000MA01ABCD0X", null, "T1", null, status, LocalDateTime.now());
        return new MerchantDetailResponse(merchant, List.of());
    }

    private String body(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
