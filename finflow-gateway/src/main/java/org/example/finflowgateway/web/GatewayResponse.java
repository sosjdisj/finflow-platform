package org.example.finflowgateway.web;

/**
 * 网关侧统一响应结构：与 backend 的 ApiResponse 字段完全一致（code / message / data / traceId），
 * 商户端只解析一种结构，不需要区分请求被网关还是 backend 拒绝。
 */
public record GatewayResponse(int code, String message, Object data, String traceId) {
}
