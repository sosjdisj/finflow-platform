package org.example.fakechannel.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.fakechannel.sign.SignatureService;
import org.example.fakechannel.web.dto.ChannelReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 入站验签过滤器：对所有 {@code /channel/**} 校验 {@code X-Channel-Code / X-Timestamp / X-Signature}。
 *
 * <p>签名不匹配或时间戳超出 5 分钟窗口 → 拒绝（HTTP 401，信封 code=40001）。验签通过后用
 * {@link CachedBodyHttpServletRequest} 回放请求体给控制器读取。</p>
 */
@Component
public class SignatureInterceptor extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SignatureInterceptor.class);

    private static final String HEADER_CHANNEL_CODE = "X-Channel-Code";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_SIGNATURE = "X-Signature";

    private final SignatureService signatureService;

    public SignatureInterceptor(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    /** 只拦截 /channel/**。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/channel/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String channelCode = request.getHeader(HEADER_CHANNEL_CODE);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);

        SignatureService.VerifyResult result =
                signatureService.verify(timestamp, channelCode, new String(body, StandardCharsets.UTF_8), signature);

        if (result != SignatureService.VerifyResult.OK) {
            log.warn("渠道入站验签失败 uri={} channelCode={} timestamp={} 原因={}",
                    request.getRequestURI(), channelCode, timestamp, result);
            writeUnauthorized(response, reasonOf(result));
            return;
        }

        log.debug("渠道入站验签通过 uri={} channelCode={}", request.getRequestURI(), channelCode);
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private static String reasonOf(SignatureService.VerifyResult result) {
        return switch (result) {
            case MALFORMED -> "签名头缺失或时间戳格式非法";
            case TIMESTAMP_EXPIRED -> "时间戳超出 5 分钟有效窗口";
            case SIGNATURE_MISMATCH -> "签名不匹配";
            case OK -> "";
        };
    }

    private static void writeUnauthorized(HttpServletResponse response, String reason) throws IOException {
        String message = "签名校验失败: " + reason;
        String json = "{\"code\":\"" + ChannelReply.CODE_SIGN_FAIL + "\",\"message\":\"" + message
                + "\",\"data\":null}";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
