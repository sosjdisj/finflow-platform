package org.example.finflowbackend.payment.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.payment.entity.PaymentTransaction;
import org.example.finflowbackend.payment.mapper.PaymentTransactionMapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 渠道交互留痕：每次渠道交互（请求、响应、渠道单号）各写一行，只增不改。
 */
@Service
public class PaymentTransactionService {

    private final PaymentTransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;

    public PaymentTransactionService(PaymentTransactionMapper transactionMapper, ObjectMapper objectMapper) {
        this.transactionMapper = transactionMapper;
        this.objectMapper = objectMapper;
    }

    /** 记录一次渠道交互，request/response 序列化为 JSON 存储。 */
    public void record(Long orderId, String channelCode, String txnType, String channelOrderNo,
                       Object request, Object response) {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrderId(orderId);
        txn.setChannelCode(channelCode);
        txn.setTxnType(txnType);
        txn.setChannelOrderNo(channelOrderNo);
        txn.setRequestPayload(objectMapper.writeValueAsString(request));
        txn.setResponsePayload(objectMapper.writeValueAsString(response));
        transactionMapper.insert(txn);
    }

    /** 按订单查渠道交互留痕，时间正序。 */
    public List<PaymentTransaction> listByOrder(Long orderId) {
        return transactionMapper.selectList(
                Wrappers.<PaymentTransaction>query().eq("order_id", orderId).orderByAsc("id"));
    }

    /**
     * 实际收款渠道码（S23 清分用）：订单的 PAYMENT 交互行（最新一条）。
     * 无记录返回 null（事件先于留痕落库等极端竞态，由调用方兜底）。
     */
    public String findChannelCode(Long orderId) {
        PaymentTransaction txn = latestPaymentTxn(orderId);
        return txn == null ? null : txn.getChannelCode();
    }

    /**
     * 实际支付渠道单号（退款受理用）：订单的 PAYMENT 交互行（最新一条）。
     * 无记录返回 null（退款侧凭此向渠道发起退款，缺失时由渠道拒绝受理）。
     */
    public String findPaymentChannelOrderNo(Long orderId) {
        PaymentTransaction txn = latestPaymentTxn(orderId);
        return txn == null ? null : txn.getChannelOrderNo();
    }

    /** 订单最新一条 PAYMENT 交互留痕；无记录返回 null。 */
    private PaymentTransaction latestPaymentTxn(Long orderId) {
        List<PaymentTransaction> txns = transactionMapper.selectList(
                Wrappers.<PaymentTransaction>query()
                        .eq("order_id", orderId)
                        .eq("txn_type", "PAYMENT")
                        .orderByDesc("id")
                        .last("LIMIT 1"));
        return txns.isEmpty() ? null : txns.getFirst();
    }
}
