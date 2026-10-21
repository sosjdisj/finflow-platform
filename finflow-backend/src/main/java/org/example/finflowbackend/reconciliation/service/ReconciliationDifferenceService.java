package org.example.finflowbackend.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.ledger.entity.AccountLedger;
import org.example.finflowbackend.ledger.service.LedgerService;
import org.example.finflowbackend.reconciliation.ReconciliationDifferenceStatus;
import org.example.finflowbackend.reconciliation.ReconciliationErrorCode;
import org.example.finflowbackend.reconciliation.entity.ReconciliationDifference;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationDifferenceMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * 差异单处理流（S29）：发现异常（S28 匹配记录）→ 人工发起 → 审核确认 → 执行处理（调账/补单）→ 关闭。
 * 状态转换经集中状态机校验；推进用 CAS（where 旧状态）防并发双处理；
 * 调账走 ledger 模块调账入口（S19 流水入口，ADJUSTMENT 流水），与关单同事务，失败整体回滚。
 * 不做自动调账决策：账户、金额、处理方式均由人工确认填写。
 */
@Service
public class ReconciliationDifferenceService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationDifferenceService.class);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 可发起差异处理的匹配结果（即全部非 MATCHED 类型）。 */
    private static final Set<String> DIFFERENCE_RESULTS = Set.of(
            ReconciliationRecord.AMOUNT_MISMATCH,
            ReconciliationRecord.STATUS_MISMATCH,
            ReconciliationRecord.MISSING_IN_PLATFORM,
            ReconciliationRecord.MISSING_IN_CHANNEL);

    private final ReconciliationDifferenceMapper differenceMapper;
    private final ReconciliationRecordMapper recordMapper;
    private final LedgerService ledgerService;

    public ReconciliationDifferenceService(ReconciliationDifferenceMapper differenceMapper,
                                           ReconciliationRecordMapper recordMapper,
                                           LedgerService ledgerService) {
        this.differenceMapper = differenceMapper;
        this.recordMapper = recordMapper;
        this.ledgerService = ledgerService;
    }

    /** 由匹配记录发起差异单：快照关键字段，同记录存在未终态差异单时拒绝。 */
    @Transactional
    public ReconciliationDifference create(Long recordId, String operator) {
        ReconciliationRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ReconciliationErrorCode.RECORD_NOT_FOUND);
        }
        if (!DIFFERENCE_RESULTS.contains(record.getResult())) {
            throw new BusinessException(ReconciliationErrorCode.RECORD_NOT_DIFFERENCE,
                    "记录匹配结果为 " + record.getResult() + "，仅异常记录可发起差异处理");
        }
        Long open = differenceMapper.selectCount(new LambdaQueryWrapper<ReconciliationDifference>()
                .eq(ReconciliationDifference::getRecordId, recordId)
                .notIn(ReconciliationDifference::getStatus,
                        ReconciliationDifferenceStatus.CLOSED.name(),
                        ReconciliationDifferenceStatus.IGNORED.name()));
        if (open != null && open > 0) {
            throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_OPEN_EXISTS);
        }

        ReconciliationDifference diff = new ReconciliationDifference();
        diff.setDiffNo(nextDiffNo());
        diff.setTaskId(record.getTaskId());
        diff.setRecordId(record.getId());
        diff.setDiffType(record.getResult());
        diff.setStatus(ReconciliationDifferenceStatus.PENDING.name());
        diff.setChannelOrderNo(record.getChannelOrderNo());
        diff.setMerchantOrderNo(record.getMerchantOrderNo());
        diff.setBillAmount(record.getBillAmount());
        diff.setPlatformAmount(record.getPlatformAmount());
        diff.setBillStatus(record.getBillStatus());
        diff.setPlatformStatus(record.getPlatformStatus());
        diff.setPlatformOrderNo(record.getPlatformOrderNo());
        diff.setRemark(record.getRemark());
        diff.setCreatedBy(operator);
        try {
            differenceMapper.insert(diff);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_OPEN_EXISTS,
                    "差异单号冲突，请重试");
        }
        return diff;
    }

    /** 人工审核确认：选定处理方式并留说明（不动资金）。 */
    @Transactional
    public ReconciliationDifference confirm(Long id, String handleType, String handleRemark, String operator) {
        if (!ReconciliationDifference.HANDLE_ADJUST.equals(handleType)
                && !ReconciliationDifference.HANDLE_SUPPLEMENT.equals(handleType)) {
            throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_HANDLE_INVALID,
                    "处理方式仅支持 ADJUST（调账）/ SUPPLEMENT（补单）");
        }
        ReconciliationDifference diff = requireDifference(id);
        ReconciliationDifferenceStatus.MACHINE.checkOrThrow(currentStatus(diff),
                ReconciliationDifferenceStatus.CONFIRMED, ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID);

        LambdaUpdateWrapper<ReconciliationDifference> update = new LambdaUpdateWrapper<ReconciliationDifference>()
                .set(ReconciliationDifference::getStatus, ReconciliationDifferenceStatus.CONFIRMED.name())
                .set(ReconciliationDifference::getHandleType, handleType)
                .set(ReconciliationDifference::getHandleRemark, handleRemark)
                .set(ReconciliationDifference::getConfirmedBy, operator)
                .set(ReconciliationDifference::getConfirmedAt, LocalDateTime.now())
                .eq(ReconciliationDifference::getId, id)
                .eq(ReconciliationDifference::getStatus, ReconciliationDifferenceStatus.PENDING.name());
        casCheck(differenceMapper.update(null, update), id);
        return requireDifference(id);
    }

    /** 忽略（误报 / 暂不处理）：PENDING → IGNORED，说明随单留痕。 */
    @Transactional
    public ReconciliationDifference ignore(Long id, String remark, String operator) {
        ReconciliationDifference diff = requireDifference(id);
        ReconciliationDifferenceStatus.MACHINE.checkOrThrow(currentStatus(diff),
                ReconciliationDifferenceStatus.IGNORED, ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID);

        LambdaUpdateWrapper<ReconciliationDifference> update = new LambdaUpdateWrapper<ReconciliationDifference>()
                .set(ReconciliationDifference::getStatus, ReconciliationDifferenceStatus.IGNORED.name())
                .set(ReconciliationDifference::getHandleRemark, remark)
                .set(ReconciliationDifference::getHandledBy, operator)
                .set(ReconciliationDifference::getHandledAt, LocalDateTime.now())
                .eq(ReconciliationDifference::getId, id)
                .eq(ReconciliationDifference::getStatus, ReconciliationDifferenceStatus.PENDING.name());
        casCheck(differenceMapper.update(null, update), id);
        return requireDifference(id);
    }

    /**
     * 执行处理（CONFIRMED → CLOSED，处理成功即关闭，同事务）：
     * ADJUST 调账走 ledger 调账入口（amount 带符号），流水号回写差异单；
     * SUPPLEMENT 补单登记凭证后关闭。调账失败（余额不足等）整体回滚，差异单保持 CONFIRMED。
     */
    @Transactional
    public ReconciliationDifference handle(Long id, Long accountId, Long amount, String handleResult, String operator) {
        ReconciliationDifference diff = requireDifference(id);
        ReconciliationDifferenceStatus.MACHINE.checkOrThrow(currentStatus(diff),
                ReconciliationDifferenceStatus.CLOSED, ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID);

        LambdaUpdateWrapper<ReconciliationDifference> update = new LambdaUpdateWrapper<ReconciliationDifference>()
                .set(ReconciliationDifference::getStatus, ReconciliationDifferenceStatus.CLOSED.name())
                .set(ReconciliationDifference::getHandleResult, handleResult)
                .set(ReconciliationDifference::getHandledBy, operator)
                .set(ReconciliationDifference::getHandledAt, LocalDateTime.now())
                .eq(ReconciliationDifference::getId, id)
                .eq(ReconciliationDifference::getStatus, ReconciliationDifferenceStatus.CONFIRMED.name());
        if (ReconciliationDifference.HANDLE_ADJUST.equals(diff.getHandleType())) {
            if (accountId == null || amount == null || amount == 0) {
                throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_HANDLE_INVALID,
                        "调账处理需指定目标账户与带符号金额（正调增/负调减，不能为 0）");
            }
            AccountLedger ledger = ledgerService.adjust(accountId, amount, adjustRemark(diff, handleResult));
            update.set(ReconciliationDifference::getLedgerNo, ledger.getBizNo());
        } else if (ReconciliationDifference.HANDLE_SUPPLEMENT.equals(diff.getHandleType())) {
            if (handleResult == null || handleResult.isBlank()) {
                throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_HANDLE_INVALID,
                        "补单处理需填写补录凭证（如补录的平台订单号）");
            }
        } else {
            throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_HANDLE_INVALID,
                    "差异单未确认处理方式，请先审核确认");
        }
        casCheck(differenceMapper.update(null, update), id);
        log.info("[差异处理] 差异单已关闭: diffNo={}, handleType={}, operator={}",
                diff.getDiffNo(), diff.getHandleType(), operator);
        return requireDifference(id);
    }

    /** 差异单分页：按单号 / 任务 / 类型 / 状态 / 商户单号筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationDifference> page(long page, long size, String diffNo, Long taskId,
                                                       String diffType, String status, String merchantOrderNo) {
        Page<ReconciliationDifference> result = differenceMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ReconciliationDifference>()
                        .eq(diffNo != null && !diffNo.isBlank(), ReconciliationDifference::getDiffNo, diffNo)
                        .eq(taskId != null, ReconciliationDifference::getTaskId, taskId)
                        .eq(diffType != null && !diffType.isBlank(), ReconciliationDifference::getDiffType, diffType)
                        .eq(status != null && !status.isBlank(), ReconciliationDifference::getStatus, status)
                        .eq(merchantOrderNo != null && !merchantOrderNo.isBlank(),
                                ReconciliationDifference::getMerchantOrderNo, merchantOrderNo)
                        .orderByDesc(ReconciliationDifference::getId));
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public ReconciliationDifference get(Long id) {
        return requireDifference(id);
    }

    private ReconciliationDifference requireDifference(Long id) {
        ReconciliationDifference diff = differenceMapper.selectById(id);
        if (diff == null) {
            throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_NOT_FOUND);
        }
        return diff;
    }

    private ReconciliationDifferenceStatus currentStatus(ReconciliationDifference diff) {
        return ReconciliationDifferenceStatus.valueOf(diff.getStatus());
    }

    /** CAS 更新未命中说明状态已被并发处理，基于旧状态的判断作废。 */
    private void casCheck(int updated, Long id) {
        if (updated == 0) {
            throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID,
                    "差异单状态已被并发处理，操作作废");
        }
    }

    /** 调账流水备注：随差异单号留痕，资金流水中可反查来源差异单。 */
    private String adjustRemark(ReconciliationDifference diff, String handleResult) {
        String detail = handleResult == null || handleResult.isBlank() ? "对账差异调账" : handleResult;
        String remark = "差异单 " + diff.getDiffNo() + "（" + diff.getMerchantOrderNo() + "）: " + detail;
        return remark.length() <= 255 ? remark : remark.substring(0, 255);
    }

    /** 差异单号：DF+yyyyMMdd+6 位随机，查重重试与任务号同款。 */
    private String nextDiffNo() {
        String datePart = LocalDate.now().format(NO_DATE);
        for (int i = 0; i < 3; i++) {
            String diffNo = "DF" + datePart + "%06d".formatted(RANDOM.nextInt(1_000_000));
            if (differenceMapper.selectCount(new LambdaQueryWrapper<ReconciliationDifference>()
                    .eq(ReconciliationDifference::getDiffNo, diffNo)) == 0) {
                return diffNo;
            }
        }
        throw new BusinessException(ReconciliationErrorCode.DIFFERENCE_NOT_FOUND, "差异单号生成失败，请重试");
    }
}
