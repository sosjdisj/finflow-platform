package org.example.finflowbackend.payment.channel;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.entity.PaymentChannel;
import org.example.finflowbackend.payment.mapper.PaymentChannelMapper;
import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 渠道路由器单测：支付方式 → 渠道映射、禁用/缺失渠道拒绝。
 * 路由只读 channelCode()，此处用远程渠道实例（不做实际 HTTP 调用）。
 */
class PaymentChannelRouterTest {

    private final PaymentChannelMapper channelMapper = mock(PaymentChannelMapper.class);
    private final PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
    private final RemoteChannelProperties properties =
            new RemoteChannelProperties("http://127.0.0.1:1", "test-secret", 500, 500);
    private final ChannelSignatureService signatureService = new ChannelSignatureService(properties);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PaymentChannelRouter router = new PaymentChannelRouter(channelMapper, List.of(
            new RemoteWallet(properties, signatureService, transactionService, objectMapper),
            new RemoteBank(properties, signatureService, transactionService, objectMapper),
            new RemoteCard(properties, signatureService, transactionService, objectMapper)));

    @Test
    void routesEachPaymentMethodToItsChannel() {
        when(channelMapper.selectList(any())).thenReturn(List.of(
                channel("MOCK_WALLET", "ALIPAY,WECHAT"),
                channel("MOCK_BANK", "BANK_CARD"),
                channel("MOCK_CARD", "QUICK_CARD")));

        assertEquals("MOCK_WALLET", router.route("ALIPAY").channelCode());
        assertEquals("MOCK_WALLET", router.route("WECHAT").channelCode());
        assertEquals("MOCK_BANK", router.route("BANK_CARD").channelCode());
        assertEquals("MOCK_CARD", router.route("QUICK_CARD").channelCode());
    }

    @Test
    void noEnabledChannelRejected() {
        // enabled 过滤在 SQL 中完成（eq enabled=true），mock 返回空列表即代表无启用渠道
        when(channelMapper.selectList(any())).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class, () -> router.route("ALIPAY"));

        assertEquals(PaymentErrorCode.CHANNEL_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void unknownPaymentMethodRejected() {
        when(channelMapper.selectList(any()))
                .thenReturn(List.of(channel("MOCK_WALLET", "ALIPAY")));

        BusinessException error = assertThrows(BusinessException.class, () -> router.route("UNIONPAY"));

        assertEquals(PaymentErrorCode.CHANNEL_NOT_FOUND, error.getErrorCode());
    }

    private PaymentChannel channel(String code, String paymentMethods) {
        PaymentChannel config = new PaymentChannel();
        config.setChannelCode(code);
        config.setPaymentMethods(paymentMethods);
        config.setEnabled(true);
        return config;
    }
}
