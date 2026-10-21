package org.example.fakechannel.core;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存账本：支付单 + 退款单。
 *
 * <p>容量按受理的渠道单无界增长（模拟器场景，进程重启即清空）。受理类操作加锁以保证
 * 幂等（同一 platformOrderNo / platformRefundNo 只落一单）。</p>
 */
@Component
public class ChannelLedger {

    /** 渠道支付单：channelOrderNo -&gt; PayOrder。 */
    private final Map<String, PayOrder> paysByChannelOrderNo = new ConcurrentHashMap<>();
    /** 支付幂等索引：platformOrderNo -&gt; channelOrderNo。 */
    private final Map<String, String> payIndexByPlatformOrderNo = new ConcurrentHashMap<>();
    /** 渠道退款单：channelRefundNo -&gt; RefundOrder。 */
    private final Map<String, RefundOrder> refundsByChannelRefundNo = new ConcurrentHashMap<>();
    /** 退款幂等索引：platformRefundNo -&gt; channelRefundNo。 */
    private final Map<String, String> refundIndexByPlatformRefundNo = new ConcurrentHashMap<>();

    /**
     * 受理支付：同 platformOrderNo 幂等，已存在则直接返回原单（created=false）。
     *
     * @return 渠道支付单 + 是否为本次新建
     */
    public synchronized AcceptPayResult acceptPay(String channelOrderNo, String platformOrderNo,
                                                  String merchantOrderNo, long amount, String subject,
                                                  String paymentMethod, String billDate) {
        String existing = payIndexByPlatformOrderNo.get(platformOrderNo);
        if (existing != null) {
            return new AcceptPayResult(paysByChannelOrderNo.get(existing), false);
        }
        PayOrder order = new PayOrder(channelOrderNo, platformOrderNo, merchantOrderNo, amount,
                subject, paymentMethod, billDate);
        paysByChannelOrderNo.put(channelOrderNo, order);
        payIndexByPlatformOrderNo.put(platformOrderNo, channelOrderNo);
        return new AcceptPayResult(order, true);
    }

    /**
     * 受理退款：同 platformRefundNo 幂等，已存在则直接返回原单（created=false）。
     *
     * @return 渠道退款单 + 是否为本次新建
     */
    public synchronized AcceptRefundResult acceptRefund(String channelRefundNo, String platformRefundNo,
                                                        String channelOrderNo, long amount, String billDate) {
        String existing = refundIndexByPlatformRefundNo.get(platformRefundNo);
        if (existing != null) {
            return new AcceptRefundResult(refundsByChannelRefundNo.get(existing), false);
        }
        RefundOrder refund = new RefundOrder(channelRefundNo, platformRefundNo, channelOrderNo, amount, billDate);
        refundsByChannelRefundNo.put(channelRefundNo, refund);
        refundIndexByPlatformRefundNo.put(platformRefundNo, channelRefundNo);
        return new AcceptRefundResult(refund, true);
    }

    public PayOrder findPay(String channelOrderNo) {
        return channelOrderNo == null ? null : paysByChannelOrderNo.get(channelOrderNo);
    }

    public RefundOrder findRefund(String channelRefundNo) {
        return channelRefundNo == null ? null : refundsByChannelRefundNo.get(channelRefundNo);
    }

    /** 更新支付单终态（SUCCESS / FAILED）。 */
    public void updatePayStatus(String channelOrderNo, String status) {
        PayOrder order = paysByChannelOrderNo.get(channelOrderNo);
        if (order != null) {
            order.status = status;
        }
    }

    /** 更新退款单终态（SUCCESS / FAILED）。 */
    public void updateRefundStatus(String channelRefundNo, String status) {
        RefundOrder refund = refundsByChannelRefundNo.get(channelRefundNo);
        if (refund != null) {
            refund.status = status;
        }
    }

    /** 标记支付单已退款（对账单里该单要输出为 REFUNDED）。 */
    public void markRefunded(String channelOrderNo) {
        PayOrder order = paysByChannelOrderNo.get(channelOrderNo);
        if (order != null) {
            order.refunded = true;
        }
    }

    /** 按账单日期取渠道支付单。 */
    public List<PayOrder> findPaysByBillDate(String billDate) {
        List<PayOrder> result = new ArrayList<>();
        for (PayOrder order : paysByChannelOrderNo.values()) {
            if (billDate.equals(order.billDate)) {
                result.add(order);
            }
        }
        return result;
    }

    /** 渠道支付单。status/refunded 会被调度线程异步改写，用 volatile 保证可见性。 */
    public static class PayOrder {
        private final String channelOrderNo;
        private final String platformOrderNo;
        private final String merchantOrderNo;
        private final long amount;
        private final String subject;
        private final String paymentMethod;
        private final String billDate;
        private volatile String status = "PENDING";
        private volatile boolean refunded = false;

        PayOrder(String channelOrderNo, String platformOrderNo, String merchantOrderNo, long amount,
                 String subject, String paymentMethod, String billDate) {
            this.channelOrderNo = channelOrderNo;
            this.platformOrderNo = platformOrderNo;
            this.merchantOrderNo = merchantOrderNo;
            this.amount = amount;
            this.subject = subject;
            this.paymentMethod = paymentMethod;
            this.billDate = billDate;
        }

        public String channelOrderNo() {
            return channelOrderNo;
        }

        public String platformOrderNo() {
            return platformOrderNo;
        }

        public String merchantOrderNo() {
            return merchantOrderNo;
        }

        public long amount() {
            return amount;
        }

        public String subject() {
            return subject;
        }

        public String paymentMethod() {
            return paymentMethod;
        }

        public String billDate() {
            return billDate;
        }

        public String status() {
            return status;
        }

        public boolean refunded() {
            return refunded;
        }
    }

    /** 渠道退款单。 */
    public static class RefundOrder {
        private final String channelRefundNo;
        private final String platformRefundNo;
        private final String channelOrderNo;
        private final long amount;
        private final String billDate;
        private volatile String status = "PROCESSING";

        RefundOrder(String channelRefundNo, String platformRefundNo, String channelOrderNo,
                    long amount, String billDate) {
            this.channelRefundNo = channelRefundNo;
            this.platformRefundNo = platformRefundNo;
            this.channelOrderNo = channelOrderNo;
            this.amount = amount;
            this.billDate = billDate;
        }

        public String channelRefundNo() {
            return channelRefundNo;
        }

        public String platformRefundNo() {
            return platformRefundNo;
        }

        public String channelOrderNo() {
            return channelOrderNo;
        }

        public long amount() {
            return amount;
        }

        public String billDate() {
            return billDate;
        }

        public String status() {
            return status;
        }
    }

    /** 受理支付结果。 */
    public record AcceptPayResult(PayOrder order, boolean created) {
    }

    /** 受理退款结果。 */
    public record AcceptRefundResult(RefundOrder order, boolean created) {
    }
}
