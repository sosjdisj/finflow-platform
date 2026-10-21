package org.example.finflowbackend.merchant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.ClearingFeeRateView;
import org.example.finflowbackend.merchant.dto.MerchantRateLogResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateSetRequest;
import org.example.finflowbackend.merchant.entity.MerchantRate;
import org.example.finflowbackend.merchant.entity.MerchantRateLog;
import org.example.finflowbackend.merchant.entity.MerchantTierRate;
import org.example.finflowbackend.merchant.mapper.MerchantRateLogMapper;
import org.example.finflowbackend.merchant.mapper.MerchantRateMapper;
import org.example.finflowbackend.merchant.mapper.MerchantTierRateMapper;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.merchant.service.RateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 费率实现：设置与写历史同事务；历史记录改前改后与操作人。
 * 阶梯费率（S23）只读提供——维护入口后续步骤按需开放，避免本步越界。
 */
@Service
public class RateServiceImpl implements RateService {

    private final MerchantRateMapper rateMapper;
    private final MerchantRateLogMapper rateLogMapper;
    private final MerchantTierRateMapper tierRateMapper;
    private final MerchantService merchantService;
    private final AuditRecorder auditRecorder;

    public RateServiceImpl(MerchantRateMapper rateMapper, MerchantRateLogMapper rateLogMapper,
                           MerchantTierRateMapper tierRateMapper, MerchantService merchantService,
                           AuditRecorder auditRecorder) {
        this.rateMapper = rateMapper;
        this.rateLogMapper = rateLogMapper;
        this.tierRateMapper = tierRateMapper;
        this.merchantService = merchantService;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public MerchantRateResponse get(Long merchantId) {
        merchantService.getDetail(merchantId);
        MerchantRate rate = rateMapper.selectOne(
                Wrappers.<MerchantRate>query().eq("merchant_id", merchantId));
        return rate == null ? null : MerchantRateResponse.from(rate);
    }

    @Override
    public ClearingFeeRateView getFeeRateView(Long merchantId) {
        MerchantRateResponse rate = get(merchantId);
        List<ClearingFeeRateView.Tier> tiers = tierRateMapper.selectList(Wrappers.<MerchantTierRate>query()
                        .eq("merchant_id", merchantId).orderByAsc("amount_from"))
                .stream().map(t -> new ClearingFeeRateView.Tier(t.getAmountFrom(), t.getRateBps()))
                .toList();
        if (!tiers.isEmpty()) {
            return new ClearingFeeRateView(merchantId, ClearingFeeRateView.MODE_TIERED,
                    rate == null ? null : rate.rateBps(), rate == null ? null : rate.fixedFee(), tiers);
        }
        return new ClearingFeeRateView(merchantId, ClearingFeeRateView.MODE_PLAIN,
                rate == null ? null : rate.rateBps(), rate == null ? null : rate.fixedFee(), List.of());
    }

    @Override
    @Transactional
    public MerchantRateResponse set(Long merchantId, MerchantRateSetRequest request) {
        merchantService.getDetail(merchantId);
        Long rateBps = request.rateBps();
        Long fixedFee = request.fixedFee();
        if ((rateBps == null || rateBps == 0) && (fixedFee == null || fixedFee == 0)) {
            throw new BusinessException(MerchantErrorCode.RATE_REQUIRED);
        }

        MerchantRate existing = rateMapper.selectOne(
                Wrappers.<MerchantRate>query().eq("merchant_id", merchantId));
        MerchantRateLog logEntry = new MerchantRateLog();
        logEntry.setMerchantId(merchantId);
        logEntry.setNewRateBps(rateBps);
        logEntry.setNewFixedFee(fixedFee);
        RateSnapshot beforeSnapshot = null;
        if (existing == null) {
            MerchantRate rate = new MerchantRate();
            rate.setMerchantId(merchantId);
            rate.setRateBps(rateBps);
            rate.setFixedFee(fixedFee);
            rateMapper.insert(rate);
            existing = rate;
        } else {
            logEntry.setOldRateBps(existing.getRateBps());
            logEntry.setOldFixedFee(existing.getFixedFee());
            beforeSnapshot = new RateSnapshot(existing.getRateBps(), existing.getFixedFee());
            existing.setRateBps(rateBps);
            existing.setFixedFee(fixedFee);
            rateMapper.updateById(existing);
        }
        logEntry.setOperator(CurrentUserHolder.current().username());
        rateLogMapper.insert(logEntry);
        auditRecorder.submit(beforeSnapshot, existing);
        return MerchantRateResponse.from(existing);
    }

    @Override
    public List<MerchantRateLogResponse> logs(Long merchantId) {
        merchantService.getDetail(merchantId);
        return rateLogMapper.selectList(Wrappers.<MerchantRateLog>query()
                        .eq("merchant_id", merchantId).orderByDesc("id"))
                .stream().map(MerchantRateLogResponse::from).toList();
    }

    /** 审计快照载体：改前费率值（改后直接序列化费率实体）。 */
    private record RateSnapshot(Long rateBps, Long fixedFee) {
    }
}
