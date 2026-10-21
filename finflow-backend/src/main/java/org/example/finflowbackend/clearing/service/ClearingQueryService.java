package org.example.finflowbackend.clearing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.clearing.ClearingErrorCode;
import org.example.finflowbackend.clearing.dto.ClearingDetailResponse;
import org.example.finflowbackend.clearing.dto.ClearingOrderDetailResponse;
import org.example.finflowbackend.clearing.dto.ClearingOrderResponse;
import org.example.finflowbackend.clearing.entity.ClearingDetail;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.clearing.mapper.ClearingDetailMapper;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 清分单查询：列表分页与详情（分账明细）。供 S26 清分记录页使用。
 */
@Service
public class ClearingQueryService {

    private final ClearingOrderMapper clearingOrderMapper;
    private final ClearingDetailMapper clearingDetailMapper;

    public ClearingQueryService(ClearingOrderMapper clearingOrderMapper, ClearingDetailMapper clearingDetailMapper) {
        this.clearingOrderMapper = clearingOrderMapper;
        this.clearingDetailMapper = clearingDetailMapper;
    }

    /** 清分单分页：按平台订单号 / 商户 / 结算日筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<ClearingOrderResponse> page(long page, long size, String platformOrderNo,
                                                    Long merchantId, LocalDate settleDate) {
        Page<ClearingOrder> result = clearingOrderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ClearingOrder>()
                        .eq(platformOrderNo != null && !platformOrderNo.isBlank(),
                                ClearingOrder::getPlatformOrderNo, platformOrderNo)
                        .eq(merchantId != null, ClearingOrder::getMerchantId, merchantId)
                        .eq(settleDate != null, ClearingOrder::getSettleDate, settleDate)
                        .orderByDesc(ClearingOrder::getId));
        return PageResponse.of(result.convert(ClearingOrderResponse::from));
    }

    /** 详情：清分单 + 分账明细。 */
    @Transactional(readOnly = true)
    public ClearingOrderDetailResponse getDetail(Long id) {
        ClearingOrder order = clearingOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ClearingErrorCode.CLEARING_NOT_FOUND);
        }
        List<ClearingDetailResponse> details = clearingDetailMapper.selectList(
                        Wrappers.<ClearingDetail>lambdaQuery()
                                .eq(ClearingDetail::getClearingId, id).orderByAsc(ClearingDetail::getId))
                .stream().map(ClearingDetailResponse::from).toList();
        return new ClearingOrderDetailResponse(ClearingOrderResponse.from(order), details);
    }
}
