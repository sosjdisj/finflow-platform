package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * IP 白名单更新请求：全量覆盖；空列表 = 清空白名单。支持 IPv4 与 CIDR。
 */
public record IpWhitelistUpdateRequest(List<
        @Pattern(regexp = "(\\d{1,3}\\.){3}\\d{1,3}(/\\d{1,2})?", message = "IP 格式不合法")
        String> ips) {
}
