package org.example.finflowbackend.settlement;

import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.example.finflowbackend.settlement.service.SettlementExecuteService;
import org.example.finflowbackend.settlement.service.SettlementPostingService;
import org.example.finflowbackend.settlement.service.SettlementStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算执行编排单测：审核透传、失败标 FAILED 且不吞原异常、未知异常包装为系统错误。
 * 事务边界与动账正确性由集成测试覆盖。
 */
class SettlementExecuteServiceTest {

    private final SettlementStatusService statusService = mock(SettlementStatusService.class);
    private final SettlementPostingService postingService = mock(SettlementPostingService.class);
    private final SettlementOrderMapper orderMapper = mock(SettlementOrderMapper.class);
    private final SettlementExecuteService service =
            new SettlementExecuteService(statusService, postingService, orderMapper,
                    new AuditRecorder(JsonMapper.builder().build()));

    @Test
    void approveDelegatesToTransitionWithOperator() {
        service.approve(1L, "admin");
        verify(statusService).transition(1L, SettlementStatus.APPROVED, "审核通过", "admin");
    }

    @Test
    void executeMovesProcessingThenPostsThenSucceeds() {
        SettlementOrder order = new SettlementOrder();
        order.setId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        service.execute(1L);

        InOrder inOrder = inOrder(statusService, postingService);
        inOrder.verify(statusService).transition(1L, SettlementStatus.PROCESSING, "发起执行", "SYSTEM");
        inOrder.verify(postingService).post(order);
        // SUCCESS 推进在动账事务内，由 PostingService 调用
        verify(statusService, org.mockito.Mockito.never())
                .transition(anyLong(), eq(SettlementStatus.SUCCESS), anyString(), anyString());
    }

    @Test
    void executeMarksFailedAndRethrowsBusinessError() {
        SettlementOrder order = new SettlementOrder();
        order.setId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        BusinessException original = new BusinessException(
                SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, "备付金余额不足");
        doThrow(original).when(postingService).post(order);

        BusinessException error = assertThrows(BusinessException.class, () -> service.execute(1L));

        assertEquals(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
        InOrder inOrder = inOrder(statusService, postingService);
        inOrder.verify(postingService).post(order);
        inOrder.verify(statusService).transition(1L, SettlementStatus.FAILED, "备付金余额不足", "SYSTEM");
    }

    @Test
    void executeWrapsUnexpectedErrorAndTruncatesFailReason() {
        SettlementOrder order = new SettlementOrder();
        order.setId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        doThrow(new IllegalStateException("x".repeat(300)))
                .when(postingService).post(order);

        BusinessException error = assertThrows(BusinessException.class, () -> service.execute(1L));

        assertEquals(ErrorCode.SYSTEM_ERROR, error.getErrorCode());
        // 失败原因截断到 255（fail_reason 列宽），且带前缀
        verify(statusService).transition(eq(1L), eq(SettlementStatus.FAILED),
                eq("执行异常: " + "x".repeat(249)), eq("SYSTEM"));
    }

    @Test
    void executeStillRethrowsOriginalWhenMarkFailedAlsoFails() {
        SettlementOrder order = new SettlementOrder();
        order.setId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        BusinessException original = new BusinessException(
                SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, "原始错误");
        doThrow(original).when(postingService).post(order);
        doThrow(new IllegalStateException("标 FAILED 也失败"))
                .when(statusService).transition(anyLong(), eq(SettlementStatus.FAILED), anyString(), anyString());

        BusinessException error = assertThrows(BusinessException.class, () -> service.execute(1L));

        assertEquals(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
    }
}
