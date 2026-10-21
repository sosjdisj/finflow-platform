package org.example.finflowbackend.merchant.service.impl;

import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.MerchantCreateRequest;
import org.example.finflowbackend.merchant.dto.MerchantReviewRequest;
import org.example.finflowbackend.merchant.dto.MerchantStatusChangeRequest;
import org.example.finflowbackend.merchant.dto.MerchantUpdateRequest;
import org.example.finflowbackend.merchant.entity.Merchant;
import org.example.finflowbackend.merchant.entity.MerchantStatusLog;
import org.example.finflowbackend.merchant.mapper.MerchantMapper;
import org.example.finflowbackend.merchant.mapper.MerchantStatusLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户服务单测：名称唯一、CLOSED 保护、状态机校验（合法留痕 / 非法拒绝且不留痕）。
 */
class MerchantServiceImplTest {

    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final MerchantStatusLogMapper statusLogMapper = mock(MerchantStatusLogMapper.class);
    private final MerchantServiceImpl service = new MerchantServiceImpl(merchantMapper, statusLogMapper);

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsDuplicatedName() {
        when(merchantMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request("重复商户")));

        assertEquals(MerchantErrorCode.MERCHANT_NAME_EXISTS, error.getErrorCode());
    }

    @Test
    void createInitializesPendingStatusAndMerchantNo() {
        when(merchantMapper.selectCount(any())).thenReturn(0L);

        var response = service.create(request("新商户"));

        assertEquals("PENDING", response.status());
        assertTrue(response.merchantNo().startsWith("M"));
    }

    @Test
    void updateRejectsClosedMerchant() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "CLOSED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, updateRequest()));

        assertEquals(MerchantErrorCode.MERCHANT_CLOSED, error.getErrorCode());
        verify(merchantMapper, never()).updateById(any(Merchant.class));
    }

    @Test
    void updateRejectsNameTakenByOtherMerchant() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "ACTIVE"));
        when(merchantMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, updateRequest()));

        assertEquals(MerchantErrorCode.MERCHANT_NAME_EXISTS, error.getErrorCode());
    }

    @Test
    void changeStatusRejectsIllegalTransition() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "PENDING"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(1L, new MerchantStatusChangeRequest("ACTIVE", "跳过审核")));

        assertEquals(MerchantErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
        verify(merchantMapper, never()).updateById(any(Merchant.class));
        verify(statusLogMapper, never()).insert(any(MerchantStatusLog.class));
    }

    @Test
    void changeStatusWritesLogOnLegalTransition() {
        loginAsAdmin();
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "REVIEWING"));

        var response = service.changeStatus(1L, new MerchantStatusChangeRequest("ACTIVE", "资料齐全"));

        assertEquals("ACTIVE", response.status());
        verify(merchantMapper).updateById(any(Merchant.class));
        ArgumentCaptor<MerchantStatusLog> captor = ArgumentCaptor.forClass(MerchantStatusLog.class);
        verify(statusLogMapper).insert(captor.capture());
        MerchantStatusLog logEntry = captor.getValue();
        assertEquals("REVIEWING", logEntry.getFromStatus());
        assertEquals("ACTIVE", logEntry.getToStatus());
        assertEquals("资料齐全", logEntry.getReason());
        assertEquals("admin", logEntry.getOperator());
    }

    @Test
    void changeStatusRejectsUnknownMerchant() {
        when(merchantMapper.selectById(9L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(9L, new MerchantStatusChangeRequest("REVIEWING", null)));

        assertEquals(MerchantErrorCode.MERCHANT_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void reviewPendingMerchantAutoEntersReviewingThenActivates() {
        loginAsAdmin();
        // 三次查询：review 判断、受理审核、执行通过；mock 依次返回 PENDING / PENDING / REVIEWING
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "PENDING"),
                merchant(1L, "PENDING"), merchant(1L, "REVIEWING"));

        var response = service.review(1L, new MerchantReviewRequest(true, "资料齐全"));

        assertEquals("ACTIVE", response.status());
        ArgumentCaptor<MerchantStatusLog> captor = ArgumentCaptor.forClass(MerchantStatusLog.class);
        verify(statusLogMapper, times(2)).insert(captor.capture());
        List<MerchantStatusLog> logs = captor.getAllValues();
        assertEquals("PENDING", logs.get(0).getFromStatus());
        assertEquals("REVIEWING", logs.get(0).getToStatus());
        assertEquals("REVIEWING", logs.get(1).getFromStatus());
        assertEquals("ACTIVE", logs.get(1).getToStatus());
        assertEquals("资料齐全", logs.get(1).getReason());
    }

    @Test
    void reviewRejectsMerchantOutsideReviewing() {
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "ACTIVE"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.review(1L, new MerchantReviewRequest(false, null)));

        assertEquals(MerchantErrorCode.MERCHANT_NOT_IN_REVIEW, error.getErrorCode());
    }

    @Test
    void reviewLandsOnClosedWhenRejected() {
        loginAsAdmin();
        when(merchantMapper.selectById(1L)).thenReturn(merchant(1L, "REVIEWING"), merchant(1L, "REVIEWING"));

        var response = service.review(1L, new MerchantReviewRequest(false, "材料不实"));

        assertEquals("CLOSED", response.status());
    }

    private void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(1L, "admin"), null));
    }

    private MerchantCreateRequest request(String name) {
        return new MerchantCreateRequest(name, null, "张三", "13800000000",
                null, "91110000MA01ABCD0X", null, null);
    }

    private MerchantUpdateRequest updateRequest() {
        return new MerchantUpdateRequest("改名商户", null, "张三", "13800000000",
                null, "91110000MA01ABCD0X", null, null, "https://callback.example.com/notify");
    }

    private Merchant merchant(Long id, String status) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setMerchantNo("M20261017123456");
        merchant.setMerchantName("测试商户");
        merchant.setStatus(status);
        return merchant;
    }
}
