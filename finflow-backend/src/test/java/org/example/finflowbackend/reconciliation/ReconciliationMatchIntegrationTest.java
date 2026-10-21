package org.example.finflowbackend.reconciliation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.entity.PaymentOrderStateLog;
import org.example.finflowbackend.payment.entity.PaymentTransaction;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.mapper.PaymentOrderStateLogMapper;
import org.example.finflowbackend.payment.mapper.PaymentTransactionMapper;
import org.example.finflowbackend.reconciliation.dto.MatchSummaryResponse;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationBillItemMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.example.finflowbackend.refund.entity.RefundOrder;
import org.example.finflowbackend.refund.mapper.RefundOrderMapper;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * S28 集成验收（报告：构造一份含差异的账单，五类结果都能正确产出）：
 * 直插含差异的账单行与平台侧支付/退款/留痕/交易数据 → 匹配 → 五类结果逐项断言 + 重跑幂等。
 * 唯一随机渠道码 + 未来账单日期隔离历史运行残留；依赖本地 MySQL/Redis/Kafka，S39 换 Testcontainers。
 */
class ReconciliationMatchIntegrationTest extends IntegrationTestBase {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MERCHANT_ID = 9001L;

    @Autowired
    private ReconciliationMatchService matchService;
    @Autowired
    private ReconciliationTaskMapper taskMapper;
    @Autowired
    private ReconciliationBillItemMapper billItemMapper;
    @Autowired
    private ReconciliationRecordMapper recordMapper;
    @Autowired
    private PaymentOrderMapper paymentOrderMapper;
    @Autowired
    private PaymentOrderStateLogMapper stateLogMapper;
    @Autowired
    private PaymentTransactionMapper transactionMapper;
    @Autowired
    private RefundOrderMapper refundOrderMapper;

    /** 唯一随机串：单号/渠道码防历史残留冲突。 */
    private String uniq() {
        return Long.toHexString(System.nanoTime()) + RANDOM.nextInt(10_000);
    }

    /** 唯一随机未来账单日期（S24 踩坑同款隔离）。 */
    private LocalDate uniqFutureDate() {
        return LocalDate.now().plusDays(1 + RANDOM.nextInt(300));
    }

    private ReconciliationTask newTask(String channelCode, LocalDate billDate, String status) {
        ReconciliationTask task = new ReconciliationTask();
        task.setTaskNo("R" + billDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "%06d".formatted(RANDOM.nextInt(1_000_000)));
        task.setChannelCode(channelCode);
        task.setBillDate(billDate);
        task.setFileName("it-match.csv");
        task.setFilePath("./target/recon-it/it-match.csv");
        task.setStatus(status);
        task.setTotalRows(0);
        task.setValidRows(0);
        task.setUploadedBy("it-admin");
        taskMapper.insert(task);
        return task;
    }

    private void billRow(Long taskId, int rowNo, String channelNo, String merchantNo,
                         long amount, String status, LocalDate billDate) {
        ReconciliationBillItem item = new ReconciliationBillItem();
        item.setTaskId(taskId);
        item.setRowNo(rowNo);
        item.setChannelOrderNo(channelNo);
        item.setMerchantOrderNo(merchantNo);
        item.setAmount(amount);
        item.setBillStatus(status);
        item.setBillDate(billDate);
        billItemMapper.insert(item);
    }

    /** 平台支付单（refunded_amount 必须显式置 0）。 */
    private PaymentOrder payOrder(String merchantOrderNo, long amount, String status) {
        PaymentOrder order = new PaymentOrder();
        order.setMerchantId(MERCHANT_ID);
        order.setMerchantOrderNo(merchantOrderNo);
        order.setPlatformOrderNo("PIT" + uniq());
        order.setAmount(amount);
        order.setRefundedAmount(0L);
        order.setCurrency("CNY");
        order.setSubject("it-match");
        order.setPaymentMethod("ALIPAY");
        order.setExpiredAt(LocalDateTime.now().plusDays(1));
        order.setStatus(status);
        paymentOrderMapper.insert(order);
        return order;
    }

    /** 支付成功留痕：进 PAID 的时间即对账“当日成功”依据。 */
    private void paidLog(Long orderId, LocalDate paidDate) {
        PaymentOrderStateLog log = new PaymentOrderStateLog();
        log.setOrderId(orderId);
        log.setFromStatus("PROCESSING");
        log.setToStatus("PAID");
        log.setOperator("it");
        log.setCreatedAt(paidDate.atTime(12, 0));
        stateLogMapper.insert(log);
    }

    private void txn(Long orderId, String channelCode, String channelOrderNo) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrderId(orderId);
        tx.setChannelCode(channelCode);
        tx.setTxnType("PAYMENT");
        tx.setChannelOrderNo(channelOrderNo);
        transactionMapper.insert(tx);
    }

    private void refundSuccess(Long orderId, String platformOrderNo, String refundNo, long amount) {
        RefundOrder refund = new RefundOrder();
        refund.setRefundNo(refundNo);
        refund.setOrderId(orderId);
        refund.setMerchantId(MERCHANT_ID);
        refund.setPlatformOrderNo(platformOrderNo);
        refund.setRefundAmount(amount);
        refund.setCurrency("CNY");
        refund.setChannelCode("MOCK_WALLET");
        refund.setStatus("SUCCESS");
        refund.setOperator("it");
        refund.setRefundedAt(LocalDateTime.now());
        refundOrderMapper.insert(refund);
    }

    private String mo(String suffix) {
        return "MOIT" + suffix;
    }

    @Test
    void matchRejectsTaskNotCompleted() {
        ReconciliationTask task = newTask("ITCH" + uniq(), uniqFutureDate(), "PENDING");
        BusinessException error = assertThrows(BusinessException.class,
                () -> matchService.match(task.getId()));
        assertEquals(ReconciliationErrorCode.TASK_NOT_MATCHABLE, error.getErrorCode());
    }

    @Test
    void matchProducesAllFiveResultTypesAndRerunIsIdempotent() {
        String channelCode = "ITCH" + uniq();
        LocalDate billDate = uniqFutureDate();

        ReconciliationTask task = newTask(channelCode, billDate, "COMPLETED");

        // 1 MATCHED 支付：账单与平台一致（当日成功单，不会误报 MISSING_IN_CHANNEL）
        PaymentOrder o1 = payOrder(mo("A" + uniq()), 10000, "PAID");
        paidLog(o1.getId(), billDate);
        txn(o1.getId(), channelCode, "CHIT" + uniq());
        billRow(task.getId(), 1, "CH" + uniq(), o1.getMerchantOrderNo(), 10000, "SUCCESS", billDate);

        // 2 AMOUNT_MISMATCH 支付：账单 10000 / 平台 9900
        PaymentOrder o2 = payOrder(mo("B" + uniq()), 9900, "PAID");
        paidLog(o2.getId(), billDate);
        txn(o2.getId(), channelCode, "CHIT" + uniq());
        billRow(task.getId(), 2, "CH" + uniq(), o2.getMerchantOrderNo(), 10000, "SUCCESS", billDate);

        // 3 STATUS_MISMATCH 支付：账单 SUCCESS / 平台 FAILED
        PaymentOrder o3 = payOrder(mo("C" + uniq()), 500, "FAILED");
        billRow(task.getId(), 3, "CH" + uniq(), o3.getMerchantOrderNo(), 500, "SUCCESS", billDate);

        // 4 MATCHED 失败单：账单 FAILED / 平台 FAILED
        PaymentOrder o4 = payOrder(mo("D" + uniq()), 800, "FAILED");
        billRow(task.getId(), 4, "CH" + uniq(), o4.getMerchantOrderNo(), 800, "FAILED", billDate);

        // 5 STATUS_MISMATCH 退款：账单 REFUNDED / 平台无成功退款单
        PaymentOrder o5 = payOrder(mo("E" + uniq()), 10000, "PAID");
        paidLog(o5.getId(), billDate);
        txn(o5.getId(), channelCode, "CHIT" + uniq());
        billRow(task.getId(), 5, "CH" + uniq(), o5.getMerchantOrderNo(), 5000, "REFUNDED", billDate);

        // 6 MATCHED 退款：账单 5000 = 成功退款单 5000
        PaymentOrder o6 = payOrder(mo("F" + uniq()), 10000, "PAID");
        paidLog(o6.getId(), billDate);
        txn(o6.getId(), channelCode, "CHIT" + uniq());
        String refundNo6 = "RIT" + uniq();
        refundSuccess(o6.getId(), o6.getPlatformOrderNo(), refundNo6, 5000);
        billRow(task.getId(), 6, "CH" + uniq(), o6.getMerchantOrderNo(), 5000, "REFUNDED", billDate);

        // 7 AMOUNT_MISMATCH 退款：账单 9999 / 退款单 5000
        PaymentOrder o7 = payOrder(mo("G" + uniq()), 10000, "PAID");
        paidLog(o7.getId(), billDate);
        txn(o7.getId(), channelCode, "CHIT" + uniq());
        refundSuccess(o7.getId(), o7.getPlatformOrderNo(), "RIT" + uniq(), 5000);
        billRow(task.getId(), 7, "CH" + uniq(), o7.getMerchantOrderNo(), 9999, "REFUNDED", billDate);

        // 8 MISSING_IN_PLATFORM：平台侧无此单
        String merchantNo8 = mo("H" + uniq());
        billRow(task.getId(), 8, "CH" + uniq(), merchantNo8, 300, "SUCCESS", billDate);

        // 9 MISSING_IN_CHANNEL：平台当日成功单，账单缺行
        PaymentOrder o9 = payOrder(mo("I" + uniq()), 600, "PAID");
        paidLog(o9.getId(), billDate);
        String channelNo9 = "CHIT" + uniq();
        txn(o9.getId(), channelCode, channelNo9);

        // 10 排除：PAID 留痕不在账单日 → 不进 MISSING_IN_CHANNEL
        PaymentOrder o10 = payOrder(mo("J" + uniq()), 600, "PAID");
        paidLog(o10.getId(), billDate.plusDays(1));
        txn(o10.getId(), channelCode, "CHIT" + uniq());

        // 11 排除：交易走其他渠道 → 不进 MISSING_IN_CHANNEL
        PaymentOrder o11 = payOrder(mo("K" + uniq()), 600, "PAID");
        paidLog(o11.getId(), billDate);
        txn(o11.getId(), "OTHER_CH", "CHIT" + uniq());

        MatchSummaryResponse summary = matchService.match(task.getId());

        // 五类汇总：3 MATCHED（1/4/6）、2 AMOUNT（2/7）、2 STATUS（3/5）、1 MISSING_IN_PLATFORM、1 MISSING_IN_CHANNEL
        assertEquals(3, summary.matchedCount());
        assertEquals(2, summary.amountMismatchCount());
        assertEquals(2, summary.statusMismatchCount());
        assertEquals(1, summary.missingInPlatformCount());
        assertEquals(1, summary.missingInChannelCount());
        assertEquals(9, summary.totalRecords());

        // 逐条明细断言
        List<ReconciliationRecord> records = recordMapper.selectList(Wrappers.<ReconciliationRecord>query()
                .eq("task_id", task.getId()));
        assertEquals(9, records.size());
        Map<String, ReconciliationRecord> byMerchantNo = records.stream()
                .collect(Collectors.toMap(ReconciliationRecord::getMerchantOrderNo, Function.identity()));

        ReconciliationRecord r1 = byMerchantNo.get(o1.getMerchantOrderNo());
        assertEquals(ReconciliationRecord.MATCHED, r1.getResult());
        assertNull(r1.getRemark());

        ReconciliationRecord r2 = byMerchantNo.get(o2.getMerchantOrderNo());
        assertEquals(ReconciliationRecord.AMOUNT_MISMATCH, r2.getResult());
        assertEquals(true, r2.getRemark().contains("金额不一致: 账单 10000 / 平台 9900"));
        assertEquals(10000L, r2.getBillAmount());
        assertEquals(9900L, r2.getPlatformAmount());

        ReconciliationRecord r3 = byMerchantNo.get(o3.getMerchantOrderNo());
        assertEquals(ReconciliationRecord.STATUS_MISMATCH, r3.getResult());
        assertEquals(true, r3.getRemark().contains("状态不一致: 账单 SUCCESS / 平台 FAILED"));

        ReconciliationRecord r5 = byMerchantNo.get(o5.getMerchantOrderNo());
        assertEquals(ReconciliationRecord.STATUS_MISMATCH, r5.getResult());
        assertEquals(true, r5.getRemark().contains("平台无成功退款单"));

        ReconciliationRecord r6 = byMerchantNo.get(o6.getMerchantOrderNo());
        assertEquals(ReconciliationRecord.MATCHED, r6.getResult());
        assertEquals(refundNo6, r6.getPlatformRefundNo());

        ReconciliationRecord r7 = byMerchantNo.get(o7.getMerchantOrderNo());
        assertEquals(ReconciliationRecord.AMOUNT_MISMATCH, r7.getResult());
        assertEquals(true, r7.getRemark().contains("平台退款合计 5000"));

        ReconciliationRecord r8 = byMerchantNo.get(merchantNo8);
        assertEquals(ReconciliationRecord.MISSING_IN_PLATFORM, r8.getResult());
        assertNull(r8.getPlatformOrderNo());
        assertEquals("平台侧无此商户单号", r8.getRemark());
        assertEquals(300L, r8.getBillAmount());

        ReconciliationRecord r9 = records.stream()
                .filter(r -> ReconciliationRecord.MISSING_IN_CHANNEL.equals(r.getResult()))
                .findFirst().orElseThrow();
        assertEquals(o9.getMerchantOrderNo(), r9.getMerchantOrderNo());
        assertEquals(channelNo9, r9.getChannelOrderNo());
        assertEquals(600L, r9.getPlatformAmount());
        assertEquals("PAID", r9.getPlatformStatus());
        assertNull(r9.getBillItemId());
        assertNull(r9.getBillAmount());

        // 排除例不产生记录（MO J/K 不在结果里）
        assertNull(byMerchantNo.get(o10.getMerchantOrderNo()));
        assertNull(byMerchantNo.get(o11.getMerchantOrderNo()));

        // 重跑幂等：清旧重建，结果数量不变
        MatchSummaryResponse again = matchService.match(task.getId());
        assertEquals(summary.totalRecords(), again.totalRecords());
        assertEquals(9, recordMapper.selectCount(
                Wrappers.<ReconciliationRecord>query().eq("task_id", task.getId())));

        // 任务汇总列已回写
        ReconciliationTask reloaded = taskMapper.selectById(task.getId());
        assertEquals(3, reloaded.getMatchedCount());
        assertEquals(1, reloaded.getMissingInChannelCount());
    }
}
