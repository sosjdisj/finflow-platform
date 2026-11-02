package org.example.finflowgateway.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 白名单匹配（M13）：规则为单个 IP 或 CIDR，逗号分隔，空 = 不限制。
 * 语义与 backend 的 IpAddressMatcher 对齐：任一规则命中即通过；非法规则按不匹配处理（配置错误不放行）。
 */
public final class IpWhitelist {

    private IpWhitelist() {
    }

    public static boolean allows(String whitelist, String clientIp) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        for (String pattern : whitelist.split(",")) {
            String rule = pattern.trim();
            if (rule.isEmpty()) {
                continue;
            }
            try {
                if (matches(rule, clientIp)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 非法规则按不匹配处理，交给下一条
            }
        }
        return false;
    }

    private static boolean matches(String rule, String clientIp) {
        int slash = rule.indexOf('/');
        InetAddress client = address(clientIp);
        if (slash < 0) {
            return address(rule).equals(client);
        }
        InetAddress network = address(rule.substring(0, slash));
        int prefix = Integer.parseInt(rule.substring(slash + 1));
        byte[] networkBytes = network.getAddress();
        byte[] clientBytes = client.getAddress();
        if (networkBytes.length != clientBytes.length || prefix < 0 || prefix > networkBytes.length * 8) {
            throw new IllegalArgumentException("CIDR 前缀非法: " + rule);
        }
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (networkBytes[i] != clientBytes[i]) {
                return false;
            }
        }
        int remainingBits = prefix % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (networkBytes[fullBytes] & mask) == (clientBytes[fullBytes] & mask);
    }

    private static InetAddress address(String text) {
        try {
            return InetAddress.getByName(text);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("IP 不合法: " + text, e);
        }
    }
}
