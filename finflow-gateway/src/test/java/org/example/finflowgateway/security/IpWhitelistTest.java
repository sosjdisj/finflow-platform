package org.example.finflowgateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M13 IP 白名单匹配：空配置不限制、单 IP、CIDR、非法规则不放行。
 */
class IpWhitelistTest {

    @Test
    void blankWhitelistAllowsAnyIp() {
        assertTrue(IpWhitelist.allows(null, "10.1.2.3"));
        assertTrue(IpWhitelist.allows("  ", "10.1.2.3"));
        assertTrue(IpWhitelist.allows("", null));
    }

    @Test
    void exactIpMatchesOnlyItself() {
        assertTrue(IpWhitelist.allows("10.1.2.3,10.1.2.4", "10.1.2.3"));
        assertFalse(IpWhitelist.allows("10.1.2.3", "10.1.2.5"));
    }

    @Test
    void cidrMatchesPrefix() {
        assertTrue(IpWhitelist.allows("10.0.0.0/8", "10.20.30.40"));
        assertTrue(IpWhitelist.allows("192.168.1.0/24", "192.168.1.77"));
        assertFalse(IpWhitelist.allows("192.168.1.0/24", "192.168.2.77"));
        assertTrue(IpWhitelist.allows("10.0.0.0/24", "10.0.0.255"));
    }

    @Test
    void invalidRuleIsNotTrusted() {
        assertFalse(IpWhitelist.allows("10.0.0.0/33", "10.0.0.1"));
        assertFalse(IpWhitelist.allows("not-an-ip", "10.0.0.1"));
    }

    @Test
    void missingClientIpIsRejectedWhenWhitelistConfigured() {
        assertFalse(IpWhitelist.allows("10.0.0.0/8", null));
    }
}
