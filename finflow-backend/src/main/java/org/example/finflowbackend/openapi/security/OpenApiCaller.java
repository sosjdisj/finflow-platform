package org.example.finflowbackend.openapi.security;

/**
 * 验签通过的调用主体：控制器只从请求属性里取，不从请求体读 merchantId，
 * 避免商户伪造他人商户号。
 */
public record OpenApiCaller(Long merchantId, String apiKey) {

    /** 滤器写入 / 控制器读取的请求属性键。 */
    public static final String ATTRIBUTE = OpenApiCaller.class.getName();
}
