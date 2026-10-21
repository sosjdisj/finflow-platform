package org.example.fakechannel.web;

import org.example.fakechannel.config.FakeChannelProperties;
import org.example.fakechannel.core.ChannelLedger;
import org.example.fakechannel.core.NotifyDispatcher;
import org.example.fakechannel.web.dto.ChannelReply;
import org.example.fakechannel.web.dto.PayQueryRequest;
import org.example.fakechannel.web.dto.PayQueryResponse;
import org.example.fakechannel.web.dto.PayRequest;
import org.example.fakechannel.web.dto.PayResponse;
import org.example.fakechannel.web.dto.RefundQueryRequest;
import org.example.fakechannel.web.dto.RefundQueryResponse;
import org.example.fakechannel.web.dto.RefundRequest;
import org.example.fakechannel.web.dto.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 渠道对外接口。业务失败时 HTTP 状态码仍为 200，由信封 code=40004 表达。
 *
 * <p>所有接口均经 {@link SignatureInterceptor} 验签（{@code /channel/**} 前缀）。</p>
 */
@RestController
@RequestMapping("/channel/{channelCode}")
public class ChannelController {

    private static final Logger log = LoggerFactory.getLogger(ChannelController.class);
    private static final DateTimeFormatter BILL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String CSV_HEADER = "channel_order_no,merchant_order_no,amount,status,bill_date";

    private final ChannelLedger ledger;
    private final NotifyDispatcher dispatcher;
    private final FakeChannelProperties properties;

    public ChannelController(ChannelLedger ledger, NotifyDispatcher dispatcher, FakeChannelProperties properties) {
        this.ledger = ledger;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    /** 支付受理：同 platformOrderNo 幂等，返回同一渠道单号。 */
    @PostMapping("/pay")
    public ChannelReply pay(@PathVariable String channelCode, @RequestBody PayRequest request) throws InterruptedException {
        responseDelay();
        if (request == null || isBlank(request.platformOrderNo()) || request.amount() <= 0) {
            return ChannelReply.bizFail("参数不合法：platformOrderNo 必填、amount 必须为正");
        }
        ChannelLedger.AcceptPayResult accept = ledger.acceptPay(
                generateChannelOrderNo(channelCode),
                request.platformOrderNo(),
                request.merchantOrderNo(),
                request.amount(),
                request.subject(),
                request.paymentMethod(),
                LocalDate.now().format(BILL_DATE));

        if (accept.created()) {
            log.info("受理支付 channelCode={} platformOrderNo={} channelOrderNo={} amount={}",
                    channelCode, request.platformOrderNo(), accept.order().channelOrderNo(), request.amount());
            dispatcher.schedulePaymentResult(channelCode, accept.order().channelOrderNo(), request.platformOrderNo());
        } else {
            log.info("重复受理支付，返回同一渠道单 channelCode={} platformOrderNo={} channelOrderNo={}",
                    channelCode, request.platformOrderNo(), accept.order().channelOrderNo());
        }
        return ChannelReply.ok(new PayResponse(accept.order().channelOrderNo(), accept.order().status()));
    }

    /** 支付查询：返回账本当前状态。 */
    @PostMapping("/pay/query")
    public ChannelReply payQuery(@PathVariable String channelCode, @RequestBody PayQueryRequest request)
            throws InterruptedException {
        responseDelay();
        ChannelLedger.PayOrder order = ledger.findPay(request == null ? null : request.channelOrderNo());
        if (order == null) {
            return ChannelReply.bizFail("渠道单不存在");
        }
        return ChannelReply.ok(new PayQueryResponse(order.channelOrderNo(), order.platformOrderNo(),
                order.merchantOrderNo(), order.amount(), order.status()));
    }

    /** 退款受理：同 platformRefundNo 幂等。 */
    @PostMapping("/refund")
    public ChannelReply refund(@PathVariable String channelCode, @RequestBody RefundRequest request)
            throws InterruptedException {
        responseDelay();
        if (request == null || isBlank(request.channelOrderNo()) || isBlank(request.platformRefundNo())
                || request.amount() <= 0) {
            return ChannelReply.bizFail("参数不合法：channelOrderNo/platformRefundNo 必填、amount 必须为正");
        }
        ChannelLedger.PayOrder pay = ledger.findPay(request.channelOrderNo());
        if (pay == null) {
            return ChannelReply.bizFail("渠道单不存在");
        }
        ChannelLedger.AcceptRefundResult accept = ledger.acceptRefund(
                generateChannelRefundNo(channelCode),
                request.platformRefundNo(),
                request.channelOrderNo(),
                request.amount(),
                LocalDate.now().format(BILL_DATE));

        if (accept.created()) {
            log.info("受理退款 channelCode={} platformRefundNo={} channelRefundNo={} amount={}",
                    channelCode, request.platformRefundNo(), accept.order().channelRefundNo(), request.amount());
            dispatcher.scheduleRefundResult(channelCode, request.channelOrderNo(), pay.platformOrderNo(),
                    request.platformRefundNo(), accept.order().channelRefundNo());
        } else {
            log.info("重复受理退款，返回同一渠道退款单 channelCode={} platformRefundNo={} channelRefundNo={}",
                    channelCode, request.platformRefundNo(), accept.order().channelRefundNo());
        }
        return ChannelReply.ok(new RefundResponse(accept.order().channelRefundNo(), accept.order().status()));
    }

    /** 退款查询：返回账本当前状态。 */
    @PostMapping("/refund/query")
    public ChannelReply refundQuery(@PathVariable String channelCode, @RequestBody RefundQueryRequest request)
            throws InterruptedException {
        responseDelay();
        ChannelLedger.RefundOrder refund = ledger.findRefund(request == null ? null : request.channelRefundNo());
        if (refund == null) {
            return ChannelReply.bizFail("渠道退款单不存在");
        }
        return ChannelReply.ok(new RefundQueryResponse(refund.channelRefundNo(), refund.platformRefundNo(),
                refund.channelOrderNo(), refund.amount(), refund.status()));
    }

    /**
     * 对账单导出（CSV）：只导出该 bill_date 的已终态渠道单。
     * 带退款标记的单输出为 REFUNDED（与 FinFlow 对账模块 S27 对齐）。无数据时只返回表头。
     */
    @GetMapping(value = "/bill", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> bill(@PathVariable String channelCode,
                                       @RequestParam(name = "date", required = false) String date)
            throws InterruptedException {
        responseDelay();
        String billDate = isBlank(date) ? LocalDate.now().format(BILL_DATE) : date.trim();
        List<ChannelLedger.PayOrder> orders = ledger.findPaysByBillDate(billDate);

        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        int rows = 0;
        for (ChannelLedger.PayOrder order : orders) {
            String status = billStatus(order);
            if (status == null) {
                continue; // PENDING 尚未终态，不进对账单
            }
            csv.append(order.channelOrderNo()).append(',')
                    .append(order.merchantOrderNo() == null ? "" : order.merchantOrderNo()).append(',')
                    .append(order.amount()).append(',')
                    .append(status).append(',')
                    .append(order.billDate()).append('\n');
            rows++;
        }
        log.info("导出对账单 channelCode={} billDate={} 行数={}", channelCode, billDate, rows);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString());
    }

    /** 对账单状态：失败优先，其次退款标记，最后成功；PENDING 返回 null 表示不入账单。 */
    private static String billStatus(ChannelLedger.PayOrder order) {
        if ("FAILED".equals(order.status())) {
            return "FAILED";
        }
        if (order.refunded()) {
            return "REFUNDED";
        }
        if ("SUCCESS".equals(order.status())) {
            return "SUCCESS";
        }
        return null;
    }

    /** 渠道单号：前缀按渠道 + yyyyMMdd + 6 位随机。 */
    private String generateChannelOrderNo(String channelCode) {
        return prefixOf(channelCode) + LocalDate.now().format(BILL_DATE) + sixDigits();
    }

    /** 渠道退款单号：前缀 + yyyyMMdd + R + 6 位随机。 */
    private String generateChannelRefundNo(String channelCode) {
        return prefixOf(channelCode) + LocalDate.now().format(BILL_DATE) + "R" + sixDigits();
    }

    private static String prefixOf(String channelCode) {
        String cleaned = channelCode == null ? "" : channelCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (cleaned.length() >= 2) {
            return cleaned.substring(0, 2);
        }
        return cleaned.isEmpty() ? "FC" : cleaned;
    }

    private static String sixDigits() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private void responseDelay() throws InterruptedException {
        int ms = properties.responseDelayMs();
        if (ms > 0) {
            Thread.sleep(ms);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
