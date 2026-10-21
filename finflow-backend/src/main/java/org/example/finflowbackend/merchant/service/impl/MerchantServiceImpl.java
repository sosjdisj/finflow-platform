package org.example.finflowbackend.merchant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.MerchantStatus;
import org.example.finflowbackend.merchant.dto.MerchantCreateRequest;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantResponse;
import org.example.finflowbackend.merchant.dto.MerchantReviewRequest;
import org.example.finflowbackend.merchant.dto.MerchantStatusChangeRequest;
import org.example.finflowbackend.merchant.dto.MerchantStatusLogResponse;
import org.example.finflowbackend.merchant.dto.MerchantUpdateRequest;
import org.example.finflowbackend.merchant.entity.Merchant;
import org.example.finflowbackend.merchant.entity.MerchantStatusLog;
import org.example.finflowbackend.merchant.mapper.MerchantMapper;
import org.example.finflowbackend.merchant.mapper.MerchantStatusLogMapper;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 商户管理实现。
 * 状态校验统一走 MerchantStatus.MACHINE（公共状态机组件），状态变更与留痕同事务。
 */
@Service
public class MerchantServiceImpl implements MerchantService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantMapper merchantMapper;
    private final MerchantStatusLogMapper statusLogMapper;

    public MerchantServiceImpl(MerchantMapper merchantMapper, MerchantStatusLogMapper statusLogMapper) {
        this.merchantMapper = merchantMapper;
        this.statusLogMapper = statusLogMapper;
    }

    @Override
    public MerchantResponse create(MerchantCreateRequest request) {
        requireNameAvailable(request.merchantName(), null);
        Merchant merchant = new Merchant();
        merchant.setMerchantNo(nextMerchantNo());
        merchant.setMerchantName(request.merchantName());
        merchant.setCategory(request.category());
        merchant.setContactName(request.contactName());
        merchant.setContactPhone(request.contactPhone());
        merchant.setContactEmail(request.contactEmail());
        merchant.setBusinessLicenseNo(request.businessLicenseNo());
        merchant.setBusinessAddress(request.businessAddress());
        merchant.setSettleCycle(defaultCycle(request.settleCycle()));
        merchant.setStatus(MerchantStatus.PENDING.name());
        merchantMapper.insert(merchant);
        return MerchantResponse.from(merchant);
    }

    @Override
    public PageResponse<MerchantResponse> page(long page, long size, String status, String keyword) {
        Page<Merchant> result = merchantMapper.selectPage(new Page<>(page, size),
                Wrappers.<Merchant>query()
                        .eq(status != null && !status.isBlank(), "status", status)
                        .and(keyword != null && !keyword.isBlank(),
                                q -> q.like("merchant_name", keyword).or().like("merchant_no", keyword))
                        .orderByDesc("id"));
        return PageResponse.of(result.convert(MerchantResponse::from));
    }

    @Override
    public MerchantDetailResponse getDetail(Long id) {
        Merchant merchant = requireMerchant(id);
        List<MerchantStatusLogResponse> logs = statusLogMapper.selectList(
                        Wrappers.<MerchantStatusLog>query().eq("merchant_id", id).orderByAsc("id"))
                .stream().map(MerchantStatusLogResponse::from).toList();
        return new MerchantDetailResponse(MerchantResponse.from(merchant), logs);
    }

    @Override
    public MerchantResponse update(Long id, MerchantUpdateRequest request) {
        Merchant merchant = requireMerchant(id);
        requireNotClosed(merchant);
        requireNameAvailable(request.merchantName(), id);
        Merchant update = new Merchant();
        update.setId(id);
        update.setMerchantName(request.merchantName());
        update.setCategory(request.category());
        update.setContactName(request.contactName());
        update.setContactPhone(request.contactPhone());
        update.setContactEmail(request.contactEmail());
        update.setBusinessLicenseNo(request.businessLicenseNo());
        update.setBusinessAddress(request.businessAddress());
        update.setSettleCycle(defaultCycle(request.settleCycle()));
        update.setWebhookUrl(request.webhookUrl());
        merchantMapper.updateById(update);
        return MerchantResponse.from(requireMerchant(id));
    }

    @Override
    @Transactional
    public MerchantResponse review(Long id, MerchantReviewRequest request) {
        Merchant merchant = requireMerchant(id);
        MerchantStatus current = MerchantStatus.valueOf(merchant.getStatus());
        // PENDING 自动先转「审核中」（产生两条留痕），其余非审核中状态拒绝
        if (current == MerchantStatus.PENDING) {
            changeStatus(id, new MerchantStatusChangeRequest(MerchantStatus.REVIEWING.name(), "受理审核"));
        } else if (current != MerchantStatus.REVIEWING) {
            throw new BusinessException(MerchantErrorCode.MERCHANT_NOT_IN_REVIEW);
        }
        String target = Boolean.TRUE.equals(request.approve())
                ? MerchantStatus.ACTIVE.name() : MerchantStatus.CLOSED.name();
        return changeStatus(id, new MerchantStatusChangeRequest(target, request.opinion()));
    }

    @Override
    @Transactional
    public MerchantResponse changeStatus(Long id, MerchantStatusChangeRequest request) {
        Merchant merchant = requireMerchant(id);
        MerchantStatus from = MerchantStatus.valueOf(merchant.getStatus());
        MerchantStatus to = MerchantStatus.valueOf(request.targetStatus());
        MerchantStatus.MACHINE.checkOrThrow(from, to, MerchantErrorCode.ILLEGAL_STATUS_TRANSITION);

        Merchant update = new Merchant();
        update.setId(id);
        update.setStatus(to.name());
        merchantMapper.updateById(update);

        MerchantStatusLog logEntry = new MerchantStatusLog();
        logEntry.setMerchantId(id);
        logEntry.setFromStatus(from.name());
        logEntry.setToStatus(to.name());
        logEntry.setReason(request.reason());
        logEntry.setOperator(CurrentUserHolder.current().username());
        statusLogMapper.insert(logEntry);

        merchant.setStatus(to.name());
        return MerchantResponse.from(merchant);
    }

    private Merchant requireMerchant(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private void requireNotClosed(Merchant merchant) {
        if (MerchantStatus.CLOSED.name().equals(merchant.getStatus())) {
            throw new BusinessException(MerchantErrorCode.MERCHANT_CLOSED);
        }
    }

    private void requireNameAvailable(String name, Long excludeId) {
        Long count = merchantMapper.selectCount(
                Wrappers.<Merchant>query().eq("merchant_name", name).ne(excludeId != null, "id", excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(MerchantErrorCode.MERCHANT_NAME_EXISTS);
        }
    }

    private String defaultCycle(String settleCycle) {
        return settleCycle == null || settleCycle.isBlank() ? "T1" : settleCycle;
    }

    /** 商户编号：M + 日期 + 6 位随机数字，唯一索引兜底，碰撞时重试。 */
    private String nextMerchantNo() {
        String date = LocalDate.now().format(NO_DATE);
        for (int i = 0; i < 3; i++) {
            String no = "M" + date + (100000 + RANDOM.nextInt(900000));
            Long exists = merchantMapper.selectCount(Wrappers.<Merchant>query().eq("merchant_no", no));
            if (exists == null || exists == 0) {
                return no;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "商户编号生成失败，请重试");
    }
}
