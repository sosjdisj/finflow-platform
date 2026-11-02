package org.example.finflowbackend.openapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.common.web.CachedBodyRequest;
import org.example.finflowbackend.merchant.api.ApiKeyApi;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.merchant.api.MerchantDetailResponse;
import org.example.finflowbackend.merchant.api.MerchantStatus;
import org.example.finflowbackend.merchant.api.OpenApiCredential;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.api.OpenApiErrorMapper;
import org.example.finflowbackend.openapi.config.OpenApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * OpenAPI 接入校验过滤器（M12）：/open/v1/** 不走后台 JWT，走 AK/SK 签名。
 *
 * <p>校验顺序：凭证存在且启用 → 时间戳窗口 → IP 白名单 → 签名 → nonce 防重放 → 商户生效状态。
 * nonce 放在签名之后写入，无效请求不占用 Redis；任何拒绝都输出统一错误结构，错误码只对外码段。
 * M13 网关接上后，验签前移：带内部令牌与商户号的请求走可信路径，只复核商户状态；
 * 不带令牌的直连请求仍走以上全量校验，作为网关故障 / 回退时的兜底。</p>
 */
@Component
public class OpenApiAuthFilter extends OncePerRequestFilter {

    public static final String BASE_PATH = "/open/v1";

    private static final Logger log = LoggerFactory.getLogger(OpenApiAuthFilter.class);

    private final ApiKeyApi apiKeyApi;
    private final MerchantApi merchantApi;
    private final OpenApiNonceStore nonceStore;
    private final OpenApiProperties properties;
    private final ObjectMapper objectMapper;
    private final InternalAccessGuard internalAccessGuard;

    public OpenApiAuthFilter(ApiKeyApi apiKeyApi, MerchantApi merchantApi, OpenApiNonceStore nonceStore,
                             OpenApiProperties properties, ObjectMapper objectMapper,
                             InternalAccessGuard internalAccessGuard) {
        this.apiKeyApi = apiKeyApi;
        this.merchantApi = merchantApi;
        this.nonceStore = nonceStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.internalAccessGuard = internalAccessGuard;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(BASE_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CachedBodyRequest cached = request instanceof CachedBodyRequest bodyRequest
                ? bodyRequest : new CachedBodyRequest(request);
        try {
            OpenApiCaller caller = authenticate(cached);
            cached.setAttribute(OpenApiCaller.ATTRIBUTE, caller);
            chain.doFilter(cached, response);
        } catch (BusinessException e) {
            OpenApiErrorCode errorCode = OpenApiErrorMapper.map(e.getErrorCode());
            log.warn("[OpenAPI] 接入校验拒绝: uri={}, code={}, message={}",
                    request.getRequestURI(), errorCode.getCode(), e.getMessage());
            writeError(response, errorCode, e.getMessage());
        }
    }

    private OpenApiCaller authenticate(CachedBodyRequest request) {
        OpenApiCaller trusted = trustedCaller(request);
        if (trusted != null) {
            return trusted;
        }
        String apiKey = header(request, OpenApiSignature.HEADER_API_KEY);
        String timestamp = header(request, OpenApiSignature.HEADER_TIMESTAMP);
        String nonce = header(request, OpenApiSignature.HEADER_NONCE);
        String signature = header(request, OpenApiSignature.HEADER_SIGNATURE);
        if (apiKey == null || timestamp == null || nonce == null || signature == null) {
            throw new BusinessException(OpenApiErrorCode.AUTH_FAILED,
                    "缺少签名请求头（X-Api-Key / X-Timestamp / X-Nonce / X-Signature）");
        }

        OpenApiCredential credential = apiKeyApi.findCredential(apiKey)
                .orElseThrow(() -> new BusinessException(OpenApiErrorCode.CREDENTIAL_INVALID));

        long requestTime = parseTimestamp(timestamp);
        long now = System.currentTimeMillis();
        if (!OpenApiSignature.timestampInWindow(requestTime, now, properties.timestampWindowSeconds())) {
            throw new BusinessException(OpenApiErrorCode.TIMESTAMP_EXPIRED);
        }
        checkIpWhitelist(credential.ipWhitelist(), request.getRemoteAddr());

        String canonical = OpenApiSignature.canonical(request.getMethod(), request.getRequestURI(),
                request.getQueryString(), request.body(), timestamp, nonce);
        if (!OpenApiSignature.matches(credential.secret(), canonical, signature)) {
            throw new BusinessException(OpenApiErrorCode.AUTH_FAILED);
        }
        if (!nonceStore.tryConsume(apiKey, nonce, properties.nonceTtlSeconds())) {
            throw new BusinessException(OpenApiErrorCode.NONCE_REPLAYED);
        }

        MerchantDetailResponse merchant = merchantApi.getDetail(credential.merchantId());
        if (!MerchantStatus.ACTIVE.name().equals(merchant.merchant().status())) {
            throw new BusinessException(OpenApiErrorCode.MERCHANT_UNAVAILABLE);
        }
        return new OpenApiCaller(credential.merchantId(), apiKey);
    }

    /**
     * 网关可信路径（M13）：令牌对、商户号齐全时跳过签名 / 时间戳 / IP / nonce（这些已在网关执行），
     * 但仍复核商户 ACTIVE——网关的注入档案有短期缓存，状态变化不能靠缓存生效。
     * 令牌缺失或商户号缺失时返回 null，落回全量 AK/SK 校验，避免「半可信」请求被放过。
     */
    private OpenApiCaller trustedCaller(HttpServletRequest request) {
        if (!internalAccessGuard.matches(request.getHeader(InternalAccessGuard.HEADER_TOKEN))) {
            return null;
        }
        String merchantIdText = header(request, InternalAccessGuard.HEADER_MERCHANT_ID);
        if (merchantIdText == null) {
            return null;
        }
        Long merchantId;
        try {
            merchantId = Long.valueOf(merchantIdText);
        } catch (NumberFormatException e) {
            throw new BusinessException(OpenApiErrorCode.AUTH_FAILED, "可信商户号格式非法");
        }
        MerchantDetailResponse merchant = merchantApi.getDetail(merchantId);
        if (!MerchantStatus.ACTIVE.name().equals(merchant.merchant().status())) {
            throw new BusinessException(OpenApiErrorCode.MERCHANT_UNAVAILABLE);
        }
        return new OpenApiCaller(merchantId, header(request, OpenApiSignature.HEADER_API_KEY));
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException(OpenApiErrorCode.AUTH_FAILED, "时间戳格式非法");
        }
    }

    /** 白名单为空表示不限制；任一规则匹配即通过，规则不合法按拒绝处理（配置错误不放行）。 */
    private void checkIpWhitelist(String whitelist, String clientIp) {
        if (whitelist == null || whitelist.isBlank()) {
            return;
        }
        for (String pattern : whitelist.split(",")) {
            String rule = pattern.trim();
            if (rule.isEmpty()) {
                continue;
            }
            try {
                if (new IpAddressMatcher(rule).matches(clientIp)) {
                    return;
                }
            } catch (IllegalArgumentException e) {
                log.warn("[OpenAPI] IP 白名单规则非法，按拒绝处理: rule={}", rule);
            }
        }
        throw new BusinessException(OpenApiErrorCode.IP_NOT_ALLOWED);
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value;
    }

    private void writeError(HttpServletResponse response, OpenApiErrorCode errorCode, String message)
            throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode, message)));
    }
}
