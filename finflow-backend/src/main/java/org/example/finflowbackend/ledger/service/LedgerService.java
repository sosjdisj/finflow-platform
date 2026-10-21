package org.example.finflowbackend.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.account.AccountErrorCode;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.ledger.dto.LedgerConsistencyResponse;
import org.example.finflowbackend.ledger.dto.LedgerRecordResponse;
import org.example.finflowbackend.ledger.entity.AccountLedger;
import org.example.finflowbackend.ledger.mapper.AccountLedgerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * 资金流水服务：余额变更与流水写入同一事务（铁律 3）。
 * 余额的行锁读与 @Version 更新在 account 模块统一入口内完成，
 * 流水的 before/after 余额取自同一事务内的账户读出（before = after - 带符号 amount，分为整数，精确）。
 * 第一版约定：一致性校验针对可用余额；冻结/解冻暂不产生流水（担保支付场景引入时再补流水类型）。
 */
@Service
public class LedgerService {

    private static final DateTimeFormatter BIZ_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountLedgerMapper ledgerMapper;
    private final AccountService accountService;
    private final AuditRecorder auditRecorder;

    public LedgerService(AccountLedgerMapper ledgerMapper, AccountService accountService,
                         AuditRecorder auditRecorder) {
        this.ledgerMapper = ledgerMapper;
        this.accountService = accountService;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 入账 + 流水（同一事务）。流水唯一索引 uk_biz 冲突时整体回滚，
     * 调用方（事件消费者）可据此识别重复入账。
     *
     * @param bizType 业务类型：PAYMENT/SETTLEMENT/ADJUSTMENT
     * @param bizNo   业务单号（如平台订单号）
     * @param amount  入账金额（分，必须为正）
     * @param source  来源：PAYMENT_EVENT/CONSOLE/TASK
     */
    @Transactional
    public AccountLedger credit(Long accountId, String bizType, String bizNo, long amount, String source) {
        return credit(accountId, bizType, bizNo, amount, source, null);
    }

    /** 入账 + 流水，remark 随流水留痕（调账原因等）。 */
    @Transactional
    public AccountLedger credit(Long accountId, String bizType, String bizNo, long amount, String source, String remark) {
        Account account = accountService.credit(accountId, amount);
        return writeLedger(account, bizType, bizNo, amount, source, remark);
    }

    /**
     * 出账 + 流水（同一事务）。amount 为出账金额（分，必须为正），流水记负数；
     * 余额不足由 account 模块拒绝并整体回滚。
     */
    @Transactional
    public AccountLedger debit(Long accountId, String bizType, String bizNo, long amount, String source, String remark) {
        Account account = accountService.debit(accountId, amount);
        return writeLedger(account, bizType, bizNo, -amount, source, remark);
    }

    /**
     * 人工调账：amount 带符号（正调增、负调减），必须走流水（铁律 3）。
     * uk_biz 冲突时整体回滚（调账已动余额，禁止吞掉异常重试，否则会双重入账），
     * 单号含毫秒时间戳 + 随机段，碰撞概率可忽略。
     */
    @Transactional
    public AccountLedger adjust(Long accountId, long amount, String remark) {
        if (amount == 0) {
            throw new BusinessException(AccountErrorCode.ADJUST_AMOUNT_ZERO);
        }
        String bizNo = "ADJ" + LocalDateTime.now().format(BIZ_NO_TIME) + RANDOM.nextInt(10000);
        AccountLedger ledger = amount > 0
                ? credit(accountId, "ADJUSTMENT", bizNo, amount, "CONSOLE", remark)
                : debit(accountId, "ADJUSTMENT", bizNo, -amount, "CONSOLE", remark);
        // 人工调账审计：提交改前/改后可用余额快照（S35）
        auditRecorder.submit(Map.of("availableBalance", ledger.getBalanceBefore()),
                Map.of("availableBalance", ledger.getBalanceAfter()));
        return ledger;
    }

    /** 流水分页（管理端查询）：按账户 / 业务类型 / 业务单号 / 时间筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<LedgerRecordResponse> page(long page, long size, Long accountId, String bizType,
                                                   String bizNo, LocalDateTime createdFrom, LocalDateTime createdTo) {
        Page<AccountLedger> result = ledgerMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AccountLedger>()
                        .eq(accountId != null, AccountLedger::getAccountId, accountId)
                        .eq(bizType != null && !bizType.isBlank(), AccountLedger::getBizType, bizType)
                        .like(bizNo != null && !bizNo.isBlank(), AccountLedger::getBizNo, bizNo)
                        .ge(createdFrom != null, AccountLedger::getCreatedAt, createdFrom)
                        .le(createdTo != null, AccountLedger::getCreatedAt, createdTo)
                        .orderByDesc(AccountLedger::getId));
        return PageResponse.of(result.convert(LedgerRecordResponse::from));
    }

    /** 一致性校验：当前可用余额 = 初始余额(0) + Σ带符号流水。 */
    @Transactional(readOnly = true)
    public LedgerConsistencyResponse consistencyCheck(Long accountId) {
        Account account = accountService.getAccount(accountId);
        long ledgerSum = ledgerMapper.sumAmountByAccountId(accountId);
        long currentBalance = account.getAvailableBalance();
        return new LedgerConsistencyResponse(accountId, currentBalance, ledgerSum,
                currentBalance == ledgerSum);
    }

    /** 流水组装：余额已由 account 模块在同一事务内变更完成，before/after 从变更后的账户读出。 */
    private AccountLedger writeLedger(Account account, String bizType, String bizNo,
                                      long signedAmount, String source, String remark) {
        AccountLedger ledger = new AccountLedger();
        ledger.setAccountId(account.getId());
        ledger.setBizType(bizType);
        ledger.setBizNo(bizNo);
        ledger.setAmount(signedAmount);
        ledger.setBalanceBefore(account.getAvailableBalance() - signedAmount);
        ledger.setBalanceAfter(account.getAvailableBalance());
        ledger.setCurrency(account.getCurrency());
        ledger.setSource(source);
        ledger.setRemark(remark);
        ledgerMapper.insert(ledger);
        return ledger;
    }
}
