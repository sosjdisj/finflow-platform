package org.example.fakechannel;

import com.jayway.jsonpath.JsonPath;
import org.example.fakechannel.sign.SignatureService;
import org.example.testsupport.NotificationRecorder;
import org.example.testsupport.NotifyTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 渠道接口集成测试：验签、幂等、账本查询、异步通知（重复推送 + 签名正确）。
 *
 * <p>用 DEFINED_PORT 起真实容器，通知派发器才能通过真实 HTTP 回调到同进程的 {@code /test/notify}；
 * 通过覆盖 pay-delay-seconds=0、notify-gap-seconds=0 让通知即时到达。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18090",
                "finflow.fake-channel.notify-url=http://localhost:18090",
                "finflow.fake-channel.pay-delay-seconds=0",
                "finflow.fake-channel.notify-gap-seconds=0",
                "finflow.fake-channel.notify-times=2",
                "finflow.fake-channel.fail-rate=0.0"
        })
@AutoConfigureMockMvc
@Import(NotifyTestConfiguration.class)
class ChannelApiTest {

    /** 渠道号用 test，使通知地址 http://localhost:18090/{channelCode}/notify 正好命中 /test/notify。 */
    private static final String CHANNEL = "test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private NotificationRecorder recorder;

    @BeforeEach
    void clearRecorder() {
        recorder.clear();
    }

    @Test
    void rejectsRequestWithoutSignature() throws Exception {
        mockMvc.perform(post("/channel/test/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("PU1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40001"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsRequestWithWrongSignature() throws Exception {
        long timestamp = System.currentTimeMillis();
        mockMvc.perform(post("/channel/test/pay")
                        .header("X-Channel-Code", CHANNEL)
                        .header("X-Timestamp", String.valueOf(timestamp))
                        .header("X-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody("PW1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40001"));
    }

    @Test
    void acceptsPayWithValidSignature() throws Exception {
        mockMvc.perform(signedPay("PA1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("10000"))
                .andExpect(jsonPath("$.data.channelOrderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void duplicatePlatformOrderNoReturnsSameChannelOrderNo() throws Exception {
        String platformOrderNo = "PD" + System.nanoTime();

        String first = channelOrderNoOf(mockMvc.perform(signedPay(platformOrderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("10000"))
                .andReturn());

        String second = channelOrderNoOf(mockMvc.perform(signedPay(platformOrderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("10000"))
                .andReturn());

        assertEquals(first, second, "同 platformOrderNo 重复受理必须返回同一渠道单号");
    }

    @Test
    void payQueryReturnsLedgerStatus() throws Exception {
        String channelOrderNo = channelOrderNoOf(mockMvc.perform(signedPay("PQ" + System.nanoTime()))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(signedPost("/channel/test/pay/query",
                        "{\"channelOrderNo\":\"" + channelOrderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("10000"))
                .andExpect(jsonPath("$.data.channelOrderNo").value(channelOrderNo))
                .andExpect(jsonPath("$.data.status").isNotEmpty());
    }

    @Test
    void notifyIsPushedTwiceWithValidSignature() throws Exception {
        String platformOrderNo = "PN" + System.nanoTime();
        mockMvc.perform(signedPay(platformOrderNo)).andExpect(status().isOk());

        awaitNotifications(platformOrderNo, 2, 5000);

        List<NotificationRecorder.Received> notifications = recorder.forPlatformOrderNo(platformOrderNo);
        assertEquals(2, notifications.size(), "notifyTimes=2 应收到 2 次通知（模拟渠道重复通知）");
        for (NotificationRecorder.Received notification : notifications) {
            assertEquals(SignatureService.VerifyResult.OK,
                    signatureService.verify(notification.timestamp(), notification.channelCode(),
                            notification.body(), notification.signature()),
                    "通知签名应可被验签");
            assertNotNull(notification.channelCode());
            assertTrue(notification.body().contains("\"result\":\"SUCCESS\""),
                    "failRate=0 时通知结果应为 SUCCESS: " + notification.body());
        }
    }

    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder signedPay(String platformOrderNo) {
        return signedPost("/channel/test/pay", payBody(platformOrderNo));
    }

    private MockHttpServletRequestBuilder signedPost(String path, String body) {
        long timestamp = System.currentTimeMillis();
        String signature = signatureService.sign(String.valueOf(timestamp), CHANNEL, body);
        return post(path)
                .header("X-Channel-Code", CHANNEL)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String payBody(String platformOrderNo) {
        return "{\"platformOrderNo\":\"" + platformOrderNo + "\",\"merchantOrderNo\":\"M001\","
                + "\"amount\":10000,\"subject\":\"测试商品\",\"paymentMethod\":\"ALIPAY\"}";
    }

    private static String channelOrderNoOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.channelOrderNo");
    }

    private void awaitNotifications(String platformOrderNo, int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (recorder.forPlatformOrderNo(platformOrderNo).size() >= expected) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
