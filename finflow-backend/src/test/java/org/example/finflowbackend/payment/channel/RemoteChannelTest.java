package org.example.finflowbackend.payment.channel;

import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.example.finflowbackend.support.FakeChannelStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 远程渠道单测：经真实 HTTP 调用测试内渠道桩（{@link FakeChannelStub}），
 * 覆盖受理留痕、同步查询、退款受理、渠道拒绝与验签失败（密钥不符）等分支。
 */
class RemoteChannelTest {

    private static final String SECRET = "finflow-fake-channel-secret";

    private final FakeChannelStub stub = FakeChannelStub.get();
    private final PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RemoteChannelProperties properties =
            new RemoteChannelProperties(stub.baseUrl(), SECRET, 2000, 2000);
    private final ChannelSignatureService signatureService = new ChannelSignatureService(properties);
    private final RemoteWallet wallet =
            new RemoteWallet(properties, signatureService, transactionService, objectMapper);

    /** 单测只验同步请求/应答：关闭桩的异步通知，避免渠道单状态被异步改写。 */
    @BeforeEach
    void disableAsyncNotify() {
        stub.reset();
        stub.setNotifyBaseUrl(null);
    }

    private ChannelRequest payRequest() {
        return new ChannelRequest(1L, "P20261017000001", "M20261017000001", null,
                10000L, "ALIPAY", "测试商品", null);
    }

    @Test
    void createPaymentAcceptedAndTransactionRecorded() {
        ChannelResult result = wallet.createPayment(payRequest());

        assertTrue(result.accepted());
        assertNotNull(result.channelOrderNo());
        assertTrue(result.channelOrderNo().startsWith("MW"));
        assertEquals("PENDING", result.channelStatus());
        verify(transactionService).record(eq(1L), eq("MOCK_WALLET"), eq("PAYMENT"),
                eq(result.channelOrderNo()), any(), any());
    }

    @Test
    void queryPaymentReturnsChannelStatus() {
        String channelOrderNo = wallet.createPayment(payRequest()).channelOrderNo();

        ChannelResult query = wallet.queryPayment(new ChannelRequest(1L, "P20261017000001", null,
                channelOrderNo, 10000L, "ALIPAY", "测试商品", null));

        assertTrue(query.accepted());
        assertEquals(channelOrderNo, query.channelOrderNo());
        assertEquals("PENDING", query.channelStatus());
    }

    @Test
    void refundAcceptedForExistingPayment() {
        String channelOrderNo = wallet.createPayment(payRequest()).channelOrderNo();

        ChannelResult result = wallet.refund(new ChannelRequest(1L, "P20261017000001", "M20261017000001",
                channelOrderNo, 3000L, "ALIPAY", "测试商品", "R20261017000001"));

        assertTrue(result.accepted());
        assertNotNull(result.channelOrderNo());
        assertTrue(result.channelOrderNo().startsWith("MWR"));
        verify(transactionService).record(eq(1L), eq("MOCK_WALLET"), eq("REFUND"),
                eq(result.channelOrderNo()), any(), any());
    }

    @Test
    void refundRejectedForUnknownChannelOrder() {
        ChannelResult result = wallet.refund(new ChannelRequest(1L, "P20261017000001", "M20261017000001",
                "MW_NOT_EXIST", 3000L, "ALIPAY", "测试商品", "R20261017000001"));

        assertFalse(result.accepted());
    }

    @Test
    void queryRefundRejectedForUnknownChannelRefundNo() {
        ChannelResult result = wallet.queryRefund(new ChannelRequest(1L, "P20261017000001", null,
                "MWR_NOT_EXIST", 3000L, "ALIPAY", "测试商品", null));

        assertFalse(result.accepted());
    }

    @Test
    void wrongSecretRejectedByChannelSignatureCheck() {
        RemoteChannelProperties wrongProps =
                new RemoteChannelProperties(stub.baseUrl(), "wrong-secret", 2000, 2000);
        RemoteWallet mismatched = new RemoteWallet(wrongProps,
                new ChannelSignatureService(wrongProps), transactionService, objectMapper);

        ChannelResult result = mismatched.createPayment(payRequest());

        assertFalse(result.accepted());
    }
}
