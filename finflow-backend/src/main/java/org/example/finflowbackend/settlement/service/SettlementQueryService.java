package org.example.finflowbackend.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.settlement.SettlementErrorCode;
import org.example.finflowbackend.settlement.dto.SettlementDetailResponse;
import org.example.finflowbackend.settlement.dto.SettlementOrderDetailResponse;
import org.example.finflowbackend.settlement.dto.SettlementOrderResponse;
import org.example.finflowbackend.settlement.dto.SettlementStateLogResponse;
import org.example.finflowbackend.settlement.entity.SettlementDetail;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.entity.SettlementOrderStateLog;
import org.example.finflowbackend.settlement.mapper.SettlementDetailMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderStateLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 结算单查询：列表分页与详情（明细 + 状态留痕）。供审核执行操作与 S26 前端页面使用。
 */
@Service
public class SettlementQueryService {

    private final SettlementOrderMapper settlementOrderMapper;
    private final SettlementDetailMapper settlementDetailMapper;
    private final SettlementOrderStateLogMapper stateLogMapper;

    public SettlementQueryService(SettlementOrderMapper settlementOrderMapper,
                                  SettlementDetailMapper settlementDetailMapper,
                                  SettlementOrderStateLogMapper stateLogMapper) {
        this.settlementOrderMapper = settlementOrderMapper;
        this.settlementDetailMapper = settlementDetailMapper;
        this.stateLogMapper = stateLogMapper;
    }

    /** 结算单分页：按结算单号 / 商户 / 结算日 / 状态筛选。 */
    @Transactional(readOnly = true)
    public PageResponse<SettlementOrderResponse> page(long page, long size, String settleNo,
                                                      Long merchantId, LocalDate settleDate, String status) {
        Page<SettlementOrder> result = settlementOrderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SettlementOrder>()
                        .eq(settleNo != null && !settleNo.isBlank(), SettlementOrder::getSettleNo, settleNo)
                        .eq(merchantId != null, SettlementOrder::getMerchantId, merchantId)
                        .eq(settleDate != null, SettlementOrder::getSettleDate, settleDate)
                        .eq(status != null && !status.isBlank(), SettlementOrder::getStatus, status)
                        .orderByDesc(SettlementOrder::getId));
        return PageResponse.of(result.convert(SettlementOrderResponse::from));
    }

    /** 详情：结算单 + 逐笔明细 + 状态留痕。 */
    @Transactional(readOnly = true)
    public SettlementOrderDetailResponse getDetail(Long id) {
        SettlementOrder order = settlementOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        List<SettlementDetailResponse> details = settlementDetailMapper.selectList(
                        Wrappers.<SettlementDetail>lambdaQuery()
                                .eq(SettlementDetail::getSettlementId, id).orderByAsc(SettlementDetail::getId))
                .stream().map(SettlementDetailResponse::from).toList();
        List<SettlementStateLogResponse> stateLogs = stateLogMapper.selectList(
                        Wrappers.<SettlementOrderStateLog>lambdaQuery()
                                .eq(SettlementOrderStateLog::getSettlementId, id).orderByAsc(SettlementOrderStateLog::getId))
                .stream().map(SettlementStateLogResponse::from).toList();
        return new SettlementOrderDetailResponse(SettlementOrderResponse.from(order), details, stateLogs);
    }
}
