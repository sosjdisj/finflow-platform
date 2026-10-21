package org.example.finflowbackend.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.risk.entity.RiskEvent;
import org.example.finflowbackend.risk.mapper.RiskEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 风险事件查询（S30）：只增不改的检查留痕，分页筛选供前端事件列表页使用。
 */
@Service
public class RiskEventService {

    private final RiskEventMapper eventMapper;

    public RiskEventService(RiskEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskEvent> page(long page, long size, Long orderId, Long merchantId,
                                        String verdict, LocalDateTime createdFrom, LocalDateTime createdTo) {
        Page<RiskEvent> result = eventMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RiskEvent>()
                        .eq(orderId != null, RiskEvent::getOrderId, orderId)
                        .eq(merchantId != null, RiskEvent::getMerchantId, merchantId)
                        .eq(verdict != null && !verdict.isBlank(), RiskEvent::getVerdict, verdict)
                        .ge(createdFrom != null, RiskEvent::getCreatedAt, createdFrom)
                        .le(createdTo != null, RiskEvent::getCreatedAt, createdTo)
                        .orderByDesc(RiskEvent::getId));
        return PageResponse.of(result);
    }
}
