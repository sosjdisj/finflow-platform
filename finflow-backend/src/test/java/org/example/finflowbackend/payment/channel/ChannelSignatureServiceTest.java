package org.example.finflowbackend.payment.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道签名服务单测：待签串格式、签名可校验、篡改/过期/缺字段被拒。
 */
class ChannelSignatureServiceTest {

    private final RemoteChannelProperties properties =
            new RemoteChannelProperties("http://localhost:8090", "unit-test-secret", 1000, 1000);
    private final ChannelSignatureService service = new ChannelSignatureService(properties);

    @Test
    void canonicalJoinsTimestampCodeAndBody() {
        assertEquals("1700000000000\nMOCK_WALLET\n{}",
                ChannelSignatureService.canonical("1700000000000", "MOCK_WALLET", "{}"));
        assertEquals("1700000000000\nMOCK_WALLET\n",
                ChannelSignatureService.canonical("1700000000000", "MOCK_WALLET", null));
    }

    @Test
    void signAndVerifyRoundTrip() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String body = "{\"platformOrderNo\":\"P1\"}";
        String signature = service.sign(timestamp, "MOCK_WALLET", body);

        assertTrue(service.verify(timestamp, "MOCK_WALLET", body, signature));
    }

    @Test
    void tamperedBodyRejected() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = service.sign(timestamp, "MOCK_WALLET", "{\"amount\":1}");

        assertFalse(service.verify(timestamp, "MOCK_WALLET", "{\"amount\":2}", signature));
    }

    @Test
    void expiredTimestampRejected() {
        long stale = System.currentTimeMillis() - 6 * 60 * 1000L;
        String timestamp = String.valueOf(stale);
        String body = "{}";
        String signature = service.sign(timestamp, "MOCK_WALLET", body);

        assertFalse(service.verify(timestamp, "MOCK_WALLET", body, signature));
    }

    @Test
    void malformedInputRejected() {
        assertFalse(service.verify(null, "MOCK_WALLET", "{}", "sig"));
        assertFalse(service.verify("abc", "MOCK_WALLET", "{}", "sig"));
        assertFalse(service.verify("1", "MOCK_WALLET", "{}", null));
    }
}
