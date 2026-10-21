package org.example.finflowbackend.payment.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentStatus;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时关单：定时扫描过期未支付（CREATED/PENDING 且 expired_at 已过）订单，置为 CLOSED。
 * 与渠道回调并发时由状态机校验兜底：先到者赢，后到者转换冲突仅记日志。
 */
@Component
public class PaymentOrderCloseTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderCloseTask.class);
    /** 单批处理上限，防止一次扫描拖垮任务周期。 */
    private static final int BATCH_SIZE = 100;

    private final PaymentOrderMapper orderMapper;
    private final PaymentOrderService paymentOrderService;

    public PaymentOrderCloseTask(PaymentOrderMapper orderMapper, PaymentOrderService paymentOrderService) {
        this.orderMapper = orderMapper;
        this.paymentOrderService = paymentOrderService;
    }

    /** 每分钟扫描一次过期未支付订单。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void closeExpiredOrders() {
        List<PaymentOrder> expired = orderMapper.selectList(Wrappers.<PaymentOrder>query()
                .in("status", PaymentStatus.CREATED.name(), PaymentStatus.PENDING.name())
                .lt("expired_at", LocalDateTime.now())
                .last("LIMIT " + BATCH_SIZE));
        for (PaymentOrder order : expired) {
            try {
                paymentOrderService.changeStatus(order.getId(), PaymentStatus.CLOSED.name(),
                        "超时未支付，系统关单", "SYSTEM:TIMEOUT");
            } catch (BusinessException e) {
                // 回调/关单并发先到者赢，转换冲突属常态
                log.warn("过期订单关单冲突: orderId={}", order.getId(), e);
            }
        }
    }
}
