package org.example.finflowbackend.channel.web;

import org.example.finflowbackend.channel.service.ChannelSignatureService;
import org.example.finflowbackend.payment.api.ChannelCallbackApi;
import org.example.finflowbackend.payment.api.ChannelCallbackCommand;
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
 * 成功返回 200 + code=10000。渠道可能重复通知，幂等由 payment 侧回调处理保证。
 * 本类只做渠道协议（验签 + 报文解析），回调业务经 {@link ChannelCallbackApi} 调用 payment。
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
    private final ChannelCallbackApi callbackApi;
    private final ObjectMapper objectMapper;

    public ChannelNotifyController(ChannelSignatureService signatureService,
                                   ChannelCallbackApi callbackApi, ObjectMapper objectMapper) {
        this.signatureService = signatureService;
        this.callbackApi = callbackApi;
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

        boolean success = "SUCCESS".equals(asString(root.get("result")));
        boolean refund = "REFUND".equals(asString(root.get("type")));
        ChannelCallbackCommand command = new ChannelCallbackCommand(channelCode, refund, success,
                asString(root.get("platformOrderNo")), asString(root.get("channelOrderNo")),
                asString(root.get("channelRefundNo")), asString(root.get("platformRefundNo")),
                asString(root.get("message")));
        if (!callbackApi.handle(command)) {
            log.warn("渠道通知找不到平台订单 channelCode={} platformOrderNo={}", channelCode,
                    command.platformOrderNo());
            return ResponseEntity.ok(new ChannelAck(CODE_BIZ_FAIL, "平台订单不存在"));
        }
        log.info("渠道通知已处理 channelCode={} type={} result={}", channelCode, refund ? "REFUND" : "PAYMENT",
                success ? "SUCCESS" : "FAILED");
        return ResponseEntity.ok(new ChannelAck(CODE_SUCCESS, "OK"));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** 渠道协议响应信封（仅 code + message）。 */
    public record ChannelAck(String code, String message) {
    }
}
