package org.example.finflowgateway.security;

/**
 * 网关 ↔ backend 的内部请求头（M13）。
 * 商户请求进网关时先被清空，只有验签通过后由网关重新写入，防止商户伪造可信标记。
 */
public final class InternalHeaders {

    /** 内部令牌：取注入档案的凭证，同时是「该请求已验签」的标记。 */
    public static final String TOKEN = "X-Finflow-Internal-Token";

    /** 网关验签后写入的商户号，backend 据此构造调用主体。 */
    public static final String MERCHANT_ID = "X-Finflow-Merchant-Id";

    private InternalHeaders() {
    }
}
