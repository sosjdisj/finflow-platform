package org.example.fakechannel.sign;

import org.example.fakechannel.config.FakeChannelProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 签名服务纯单测：签名/验签往返、篡改 body 失败、时间戳超窗失败。
 */
class SignatureServiceTest {

    private final SignatureService service = new SignatureService(
            new FakeChannelProperties("unit-secret", "http://localhost:8080/api/v1/channels", 0, 0.0, 1, 0, 0));

    @Test
    void signThenVerifyRoundTrip() {
        long now = System.currentTimeMillis();
        String timestamp = String.valueOf(now);
        String body = "{\"platformOrderNo\":\"P1\"}";

        String signature = service.sign(timestamp, "alipay", body);

        assertEquals(SignatureService.VerifyResult.OK,
                service.verify(timestamp, "alipay", body, signature, now));
    }

    @Test
    void tamperedBodyFailsVerification() {
        long now = System.currentTimeMillis();
        String timestamp = String.valueOf(now);
        String body = "{\"amount\":100}";

        String signature = service.sign(timestamp, "alipay", body);
        assertNotEquals(signature, service.sign(timestamp, "alipay", body + "1"));

        assertEquals(SignatureService.VerifyResult.SIGNATURE_MISMATCH,
                service.verify(timestamp, "alipay", body + "1", signature, now));
    }

    @Test
    void expiredTimestampFailsVerification() {
        long now = System.currentTimeMillis();
        String oldTimestamp = String.valueOf(now - 10 * 60 * 1000L);
        String body = "{}";
        String signature = service.sign(oldTimestamp, "alipay", body);

        assertEquals(SignatureService.VerifyResult.TIMESTAMP_EXPIRED,
                service.verify(oldTimestamp, "alipay", body, signature, now));
    }

    @Test
    void missingHeadersAreMalformed() {
        assertEquals(SignatureService.VerifyResult.MALFORMED,
                service.verify(null, "alipay", "{}", "whatever", System.currentTimeMillis()));
    }
}
