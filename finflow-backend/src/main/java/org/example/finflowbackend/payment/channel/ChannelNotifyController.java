package org.example.finflowbackend.payment.channel;

import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 渠道异步通知接收端点：{@code POST /api/v1/channels/{channelCode}/notify}。
 * 协议：请求头 X-Channel-Code / X-Timestamp / X-Signature，签名对原始 body 生成；
 * 验签失败返回 401 + code=40001；业务异常（订单不存在等）返回 200 + code=40004；
 * 成功返回 200 + code=10000。渠道可能重复通知，幂等在 ChannelCallbackHandler 内做（按渠道单号去重）。
 * 响应信封沿用渠道协议，不复用平台 ApiResponse。
 */
@RestController
@RequestMapping("/api/v1/channels")
public class ChannelNotifyController {

    private static final Logger log = LoggerFactory.getLogger(ChannelNotifyController.class);

    private static final String CODE_SUCCESS = "10000";
    private static final String CODE_BIZ_FAIL = "40004";
    private static final String CODE_SIGN_FAIL = "40001";

    private final ChannelSignatureService signatureService;
    private final ChannelCallbackHandler callbackHandler;
    private final PaymentOrderService paymentOrderService;
    private final ObjectMapper objectMapper;

    public ChannelNotifyController(ChannelSignatureService signatureService,
                                   ChannelCallbackHandler callbackHandler,
                                   PaymentOrderService paymentOrderService, ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.callbackHandler = callbackHandler;
        this.paymentOrderService = paymentOrderService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{channelCode}/notify")
    public ResponseEntity<ChannelAck> notify(@PathVariable String channelCode,
                                             @RequestHeader(name = "X-Timestamp", required = false) String timestamp,
                                             @RequestHeader(name = "X-Signature", required = false) String signature,
                                             @RequestBody(required = false) String body) {
        String payload = body == null ? "" : body;
        if (!signatureService.verify(timestamp, channelCode, payload, signature)) {
            log.warn("渠道通知验签失败 channelCode={}", channelCode);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ChannelAck(CODE_SIGN_FAIL, "验签失败"));
        }

        Map<?, ?> root;
        try {
            root = objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            return ResponseEntity.ok(new ChannelAck(CODE_BIZ_FAIL, "通知报文解析失败"));
        }

        String platformOrderNo = asString(root.get("platformOrderNo"));
        Long orderId = platformOrderNo == null ? null
                : paymentOrderService.findIdByPlatformOrderNo(platformOrderNo);
        if (orderId == null) {
            log.warn("渠道通知找不到平台订单 channelCode={} platformOrderNo={}", channelCode, platformOrderNo);
            return ResponseEntity.ok(new ChannelAck(CODE_BIZ_FAIL, "平台订单不存在"));
        }

        boolean success = "SUCCESS".equals(asString(root.get("result")));
        String message = asString(root.get("message"));
        if (root.get("type") != null && "REFUND".equals(asString(root.get("type")))) {
            String refundNo = asString(root.get("platformRefundNo"));
            String channelRefundNo = asString(root.get("channelRefundNo"));
            if (success) {
                callbackHandler.onRefundSuccess(orderId, channelCode, channelRefundNo, refundNo);
            } else {
                callbackHandler.onRefundFailure(orderId, channelCode, channelRefundNo, refundNo, message);
            }
            log.info("渠道退款通知已处理 channelCode={} refundNo={} result={}", channelCode, refundNo,
                    success ? "SUCCESS" : "FAILED");
        } else {
            String channelOrderNo = asString(root.get("channelOrderNo"));
            if (success) {
                callbackHandler.onSuccess(orderId, channelCode, channelOrderNo);
            } else {
                callbackHandler.onFailure(orderId, channelCode, channelOrderNo, message);
            }
            log.info("渠道支付通知已处理 channelCode={} channelOrderNo={} result={}", channelCode,
                    channelOrderNo, success ? "SUCCESS" : "FAILED");
        }
        return ResponseEntity.ok(new ChannelAck(CODE_SUCCESS, "OK"));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** 渠道协议响应信封（仅 code + message）。 */
    public record ChannelAck(String code, String message) {
    }
}
