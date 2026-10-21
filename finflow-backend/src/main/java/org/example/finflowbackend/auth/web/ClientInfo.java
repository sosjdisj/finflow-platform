package org.example.finflowbackend.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端信息：登录日志与设备记录使用。
 * IP 优先取 X-Forwarded-For 首段（经 Nginx 反代场景），否则取直连地址。
 */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return new ClientInfo(ip, request.getHeader("User-Agent"));
    }
}
