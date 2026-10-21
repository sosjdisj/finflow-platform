package org.example.finflowbackend.reconciliation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.ledger.entity.AccountLedger;
import org.example.finflowbackend.ledger.mapper.AccountLedgerMapper;
import org.example.finflowbackend.ledger.service.LedgerService;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.entity.PaymentOrderStateLog;
import org.example.finflowbackend.payment.entity.PaymentTransaction;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.mapper.PaymentOrderStateLogMapper;
import org.example.finflowbackend.payment.mapper.PaymentTransactionMapper;
import org.example.finflowbackend.reconciliation.entity.ReconciliationBillItem;
import org.example.finflowbackend.reconciliation.entity.ReconciliationDifference;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationBillItemMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationDifferenceMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationDifferenceService;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S29 集成验收（报告：一条 AMOUNT_MISMATCH 记录走完整处理流程并关闭）：
 * 匹配产出差异 → 发起 → 确认（调账）→ 执行（走 S19 调账入口动余额）→ 关闭，
 * 另覆盖 忽略流 / 补单流 / MATCHED 拒绝 / 重复发起拒绝 / 非法状态转换拒绝。
 * 唯一随机渠道码与商户 ID 隔离历史运行残留；依赖本地 MySQL/Redis/Kafka，S39 换 Testcontainers。
 */
class ReconciliationDifferenceIntegrationTest extends IntegrationTestBase {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private ReconciliationMatchService matchService;
    @Autowired
    private ReconciliationDifferenceService differenceService;
    @Autowired
    private ReconciliationTaskMapper taskMapper;
    @Autowired
    private ReconciliationBillItemMapper billItemMapper;
    @Autowired
    private ReconciliationRecordMapper recordMapper;
    @Autowired
    private ReconciliationDifferenceMapper differenceMapper;
    @Autowired
    private PaymentOrderMapper paymentOrderMapper;
    @Autowired
    private PaymentOrderStateLogMapper stateLogMapper;
    @Autowired
    private PaymentTransactionMapper transactionMapper;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private AccountLedgerMapper ledgerMapper;

    private String uniq() {
        return Long.toHexString(System.nanoTime()) + RANDOM.nextInt(10_000);
    }

    private LocalDate uniqFutureDate() {
        return LocalDate.now().plusDays(1 + RANDOM.nextInt(300));
    }

    // ---------- 造数：任务 + 账单行 + 平台侧数据（与 S28 验收同款口径） ----------

    private ReconciliationTask newCompletedTaskWithBill(String channelCode, LocalDate billDate,
                                                        String merchantOrderNo, long billAmount,
                                                        Long platformOrderId) {
        ReconciliationTask task = new ReconciliationTask();
        task.setTaskNo("R" + billDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "%06d".formatted(RANDOM.nextInt(1_000_000)));
        task.setChannelCode(channelCode);
        task.setBillDate(billDate);
        task.setFileName("it-diff.csv");
        task.setFilePath("./target/recon-it/it-diff.csv");
        task.setStatus("COMPLETED");
        task.setTotalRows(1);
        task.setValidRows(1);
        task.setUploadedBy("it-admin");
        taskMapper.insert(task);

        ReconciliationBillItem item = new ReconciliationBillItem();
        item.setTaskId(task.getId());
        item.setRowNo(1);
        item.setChannelOrderNo("CH" + uniq());
        item.setMerchantOrderNo(merchantOrderNo);
        item.setAmount(billAmount);
        item.setBillStatus("SUCCESS");
        item.setBillDate(billDate);
        billItemMapper.insert(item);

        if (platformOrderId != null) {
            txn(platformOrderId, channelCode);
        }
        return task;
    }

    /** 平台支付单（默认 PAID 且当日成功留痕），返回订单 ID。 */
    private Long payOrder(Long merchantId, String merchantOrderNo, long amount, LocalDate billDate) {
        PaymentOrder order = new PaymentOrder();
        order.setMerchantId(merchantId);
        order.setMerchantOrderNo(merchantOrderNo);
        order.setPlatformOrderNo("PIT" + uniq());
        order.setAmount(amount);
        order.setRefundedAmount(0L);
        order.setCurrency("CNY");
        order.setSubject("it-diff");
        order.setPaymentMethod("ALIPAY");
        order.setExpiredAt(LocalDateTime.now().plusDays(1));
        order.setStatus("PAID");
        paymentOrderMapper.insert(order);

        PaymentOrderStateLog log = new PaymentOrderStateLog();
        log.setOrderId(order.getId());
        log.setFromStatus("PROCESSING");
        log.setToStatus("PAID");
        log.setOperator("it");
        log.setCreatedAt(billDate.atTime(12, 0));
        stateLogMapper.insert(log);
        return order.getId();
    }

    private void txn(Long orderId, String channelCode) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrderId(orderId);
        tx.setChannelCode(channelCode);
        tx.setTxnType("PAYMENT");
        tx.setChannelOrderNo("CHIT" + uniq());
        transactionMapper.insert(tx);
    }

    /** 取某任务某类结果的第一条匹配记录。 */
    private ReconciliationRecord firstRecord(Long taskId, String result) {
        return recordMapper.selectList(Wrappers.<ReconciliationRecord>query()
                        .eq("task_id", taskId).eq("result", result))
                .stream().findFirst().orElseThrow();
    }

    // ---------- 验收主流程：AMOUNT_MISMATCH 走完整处理流并关闭 ----------

    @Test
    void amountMismatchGoesThroughFullFlowAndCloses() {
        String channelCode = "ITCH" + uniq();
        LocalDate billDate = uniqFutureDate();
        Long merchantId = 900_000L + RANDOM.nextInt(100_000);

        // 平台 9900 / 账单 10000 → AMOUNT_MISMATCH
        Long orderId = payOrder(merchantId, "MODF" + uniq(), 9900, billDate);
        ReconciliationTask task = newCompletedTaskWithBill(channelCode, billDate,
                paymentOrderMapper.selectById(orderId).getMerchantOrderNo(), 10000, orderId);

        matchService.match(task.getId());
        ReconciliationRecord record = firstRecord(task.getId(), ReconciliationRecord.AMOUNT_MISMATCH);

        // 1. 发起差异单（快照校验）
        ReconciliationDifference diff = differenceService.create(record.getId(), "it-admin");
        assertNotNull(diff.getDiffNo());
        assertEquals(ReconciliationDifferenceStatus.PENDING.name(), diff.getStatus());
        assertEquals(10000L, diff.getBillAmount());
        assertEquals(9900L, diff.getPlatformAmount());

        // 2. 审核确认：调账
        ReconciliationDifference confirmed = differenceService.confirm(
                diff.getId(), ReconciliationDifference.HANDLE_ADJUST, "核实为渠道多收", "it-op");
        assertEquals(ReconciliationDifferenceStatus.CONFIRMED.name(), confirmed.getStatus());

        // 3. 执行处理：商户结算户调减 100（走 S19 调账入口），处理成功即关闭
        Account account = accountService.getOrCreate(AccountOwnerType.MERCHANT, merchantId, "CNY");
        ledgerService.credit(account.getId(), "PAYMENT", "FIT" + uniq(), 10000, "CONSOLE", "预置余额");
        long balanceBefore = accountService.getAccount(account.getId()).getAvailableBalance();

        ReconciliationDifference closed = differenceService.handle(
                diff.getId(), account.getId(), -100L, "平台多记，调减商户户 100 分", "it-op");

        assertEquals(ReconciliationDifferenceStatus.CLOSED.name(), closed.getStatus());
        assertNotNull(closed.getLedgerNo());
        assertNotNull(closed.getHandledAt());

        // 4. 资金侧断言：余额 -100，存在指向本差异单的 ADJUSTMENT 流水
        assertEquals(balanceBefore - 100, accountService.getAccount(account.getId()).getAvailableBalance());
        AccountLedger adjustLedger = ledgerMapper.selectOne(Wrappers.<AccountLedger>query()
                .eq("biz_no", closed.getLedgerNo()));
        assertEquals("ADJUSTMENT", adjustLedger.getBizType());
        assertEquals(-100L, adjustLedger.getAmount());
        assertTrue(adjustLedger.getRemark().contains(diff.getDiffNo()));
    }

    // ---------- 忽略流 / 补单流 ----------

    @Test
    void ignoreFlowAndSupplementFlow() {
        String channelCode = "ITCH" + uniq();
        LocalDate billDate = uniqFutureDate();
        Long merchantId = 900_000L + RANDOM.nextInt(100_000);

        // 任务 A：AMOUNT_MISMATCH → 忽略
        Long orderA = payOrder(merchantId, "MODF" + uniq(), 500, billDate);
        ReconciliationTask taskA = newCompletedTaskWithBill(channelCode, billDate,
                paymentOrderMapper.selectById(orderA).getMerchantOrderNo(), 600, orderA);
        matchService.match(taskA.getId());
        ReconciliationDifference diffA = differenceService.create(
                firstRecord(taskA.getId(), ReconciliationRecord.AMOUNT_MISMATCH).getId(), "it-admin");
        ReconciliationDifference ignored = differenceService.ignore(diffA.getId(), "渠道侧账务错误，线下催正", "it-op");
        assertEquals(ReconciliationDifferenceStatus.IGNORED.name(), ignored.getStatus());
        // 终态后不可再操作
        BusinessException again = assertThrows(BusinessException.class,
                () -> differenceService.confirm(diffA.getId(), ReconciliationDifference.HANDLE_ADJUST, "x", "it-op"));
        assertEquals(ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID, again.getErrorCode());

        // 任务 B：MISSING_IN_PLATFORM → 补单流（登记补录凭证后关闭）
        ReconciliationTask taskB = newCompletedTaskWithBill(channelCode + "B", billDate,
                "MODF" + uniq(), 300, null);
        matchService.match(taskB.getId());
        ReconciliationRecord missing = firstRecord(taskB.getId(), ReconciliationRecord.MISSING_IN_PLATFORM);
        ReconciliationDifference diffB = differenceService.create(missing.getId(), "it-admin");
        differenceService.confirm(diffB.getId(), ReconciliationDifference.HANDLE_SUPPLEMENT, "补录平台订单", "it-op");
        // SUPPLEMENT 缺补录凭证被拒
        BusinessException badHandle = assertThrows(BusinessException.class,
                () -> differenceService.handle(diffB.getId(), null, null, null, "it-op"));
        assertEquals(ReconciliationErrorCode.DIFFERENCE_HANDLE_INVALID, badHandle.getErrorCode());

        ReconciliationDifference closed = differenceService.handle(
                diffB.getId(), null, null, "已补录平台订单 PIT123", "it-op");
        assertEquals(ReconciliationDifferenceStatus.CLOSED.name(), closed.getStatus());
        assertNull(closed.getLedgerNo());
    }

    // ---------- 守卫：MATCHED 不可发起 / 重复发起拒绝 / PENDING 不可直接执行 ----------

    @Test
    void guardsRejectInvalidOperations() {
        String channelCode = "ITCH" + uniq();
        LocalDate billDate = uniqFutureDate();
        Long merchantId = 900_000L + RANDOM.nextInt(100_000);

        // MATCHED 单：金额一致 → 发起被拒
        Long orderM = payOrder(merchantId, "MODF" + uniq(), 800, billDate);
        ReconciliationTask task = newCompletedTaskWithBill(channelCode, billDate,
                paymentOrderMapper.selectById(orderM).getMerchantOrderNo(), 800, orderM);
        matchService.match(task.getId());
        ReconciliationRecord matched = firstRecord(task.getId(), ReconciliationRecord.MATCHED);
        BusinessException notDiff = assertThrows(BusinessException.class,
                () -> differenceService.create(matched.getId(), "it-admin"));
        assertEquals(ReconciliationErrorCode.RECORD_NOT_DIFFERENCE, notDiff.getErrorCode());

        // 同记录已有未终态差异单 → 重复发起被拒
        Long orderD = payOrder(merchantId, "MODF" + uniq(), 700, billDate);
        ReconciliationTask taskD = newCompletedTaskWithBill(channelCode + "D", billDate,
                paymentOrderMapper.selectById(orderD).getMerchantOrderNo(), 900, orderD);
        matchService.match(taskD.getId());
        ReconciliationRecord mismatch = firstRecord(taskD.getId(), ReconciliationRecord.AMOUNT_MISMATCH);
        ReconciliationDifference diff = differenceService.create(mismatch.getId(), "it-admin");
        BusinessException dup = assertThrows(BusinessException.class,
                () -> differenceService.create(mismatch.getId(), "it-admin"));
        assertEquals(ReconciliationErrorCode.DIFFERENCE_OPEN_EXISTS, dup.getErrorCode());

        // PENDING 不可直接执行处理（必须先审核确认）
        BusinessException pendingHandle = assertThrows(BusinessException.class,
                () -> differenceService.handle(diff.getId(), 1L, 100L, "x", "it-op"));
        assertEquals(ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID, pendingHandle.getErrorCode());

        // 调账确认后，缺账户/金额参数被拒，且差异单未被关闭
        differenceService.confirm(diff.getId(), ReconciliationDifference.HANDLE_ADJUST, "调账", "it-op");
        BusinessException noParams = assertThrows(BusinessException.class,
                () -> differenceService.handle(diff.getId(), null, null, "x", "it-op"));
        assertEquals(ReconciliationErrorCode.DIFFERENCE_HANDLE_INVALID, noParams.getErrorCode());
        assertEquals(ReconciliationDifferenceStatus.CONFIRMED.name(),
                differenceService.get(diff.getId()).getStatus());
        assertFalse(differenceMapper.selectCount(Wrappers.<ReconciliationDifference>query()
                .eq("record_id", mismatch.getId())
                .eq("status", ReconciliationDifferenceStatus.CLOSED.name())) > 0);
    }
}
