package org.example.finflowbackend.payment.task;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 超时关单任务单测：过期单逐笔关单；单笔冲突不中断整批。
 */
class PaymentOrderCloseTaskTest {

    private final PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
    private final PaymentOrderService paymentOrderService = mock(PaymentOrderService.class);

    private final PaymentOrderCloseTask task = new PaymentOrderCloseTask(orderMapper, paymentOrderService);

    @Test
    void closesEachExpiredOrder() {
        whenSelectReturns(order(11L), order(12L));

        task.closeExpiredOrders();

        verify(paymentOrderService).changeStatus(11L, "CLOSED", "超时未支付，系统关单", "SYSTEM:TIMEOUT");
        verify(paymentOrderService).changeStatus(12L, "CLOSED", "超时未支付，系统关单", "SYSTEM:TIMEOUT");
    }

    @Test
    void conflictOnOneOrderDoesNotStopBatch() {
        whenSelectReturns(order(11L), order(12L));
        doThrow(new BusinessException(PaymentErrorCode.ILLEGAL_STATUS_TRANSITION))
                .when(paymentOrderService).changeStatus(11L, "CLOSED", "超时未支付，系统关单", "SYSTEM:TIMEOUT");

        task.closeExpiredOrders();

        verify(paymentOrderService).changeStatus(12L, "CLOSED", "超时未支付，系统关单", "SYSTEM:TIMEOUT");
    }

    private void whenSelectReturns(PaymentOrder... orders) {
        org.mockito.Mockito.when(orderMapper.selectList(any())).thenReturn(List.of(orders));
    }

    private PaymentOrder order(Long id) {
        PaymentOrder order = new PaymentOrder();
        order.setId(id);
        return order;
    }
}
