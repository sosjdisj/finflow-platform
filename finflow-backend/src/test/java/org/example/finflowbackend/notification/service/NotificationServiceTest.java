package org.example.finflowbackend.notification.service;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantResponse;
import org.example.finflowbackend.merchant.entity.Merchant;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.notification.NotificationProperties;
import org.example.finflowbackend.notification.entity.Notification;
import org.example.finflowbackend.notification.entity.NotificationTemplate;
import org.example.finflowbackend.notification.mapper.NotificationMapper;
import org.example.finflowbackend.notification.mapper.NotificationTemplateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务单测（S33）：渲染落库即站内信送达、邮件三态（SENT/SKIPPED/FAILED）不影响站内信、
 * uk(event_id) 幂等兜底、模板缺失报错；平台通知走 USER 收件、不查商户。
 */
class NotificationServiceTest {

    private static final NotificationTemplate TEMPLATE = template();

    private final NotificationTemplateMapper templateMapper = mock(NotificationTemplateMapper.class);
    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);
    private final MailChannel mailChannel = mock(MailChannel.class);
    private final MerchantService merchantService = mock(MerchantService.class);

    private final NotificationService service = new NotificationService(
            templateMapper, notificationMapper, new TemplateRenderer(), mailChannel, merchantService,
            new NotificationProperties(null, null));

    @Test
    void rendersPersistsAndSendsEmail() {
        when(templateMapper.selectOne(any())).thenReturn(TEMPLATE);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        stubMerchantEmail("m@test.com");
        when(mailChannel.send(eq("m@test.com"), any(), any()))
                .thenReturn(new MailChannel.MailResult("SENT", null));

        NotificationService.SendOutcome outcome = service.sendMerchantNotification(
                "PAYMENT_SUCCEEDED", "evt-1", 1L,
                Map.of("platformOrderNo", "PO1", "amount", "100.00", "currency", "CNY", "paidAt", "2026-10-18 15:00:00"));

        // 站内信：渲染后内容落库即送达
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification saved = captor.getValue();
        assertEquals("MERCHANT", saved.getReceiverType());
        assertEquals(1L, saved.getReceiverId());
        assertEquals("SENT", saved.getInAppStatus());
        assertTrue(saved.getTitle().contains("PO1"));
        assertTrue(saved.getContent().contains("100.00 CNY"));

        // 邮件渠道状态回写
        verify(notificationMapper).update(isNull(), any());
        assertEquals("SENT", outcome.emailStatus());
    }

    @Test
    void mailSkippedStillDeliversInApp() {
        when(templateMapper.selectOne(any())).thenReturn(TEMPLATE);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        stubMerchantEmail(null);
        when(mailChannel.send(isNull(), any(), any()))
                .thenReturn(new MailChannel.MailResult("SKIPPED", "收件人无邮箱"));

        NotificationService.SendOutcome outcome = service.sendMerchantNotification(
                "PAYMENT_SUCCEEDED", "evt-2", 1L, Map.of());

        verify(notificationMapper).insert(any(Notification.class));
        assertEquals("SKIPPED", outcome.emailStatus());
    }

    @Test
    void mailFailureRecordedButInAppKept() {
        when(templateMapper.selectOne(any())).thenReturn(TEMPLATE);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        stubMerchantEmail("m@test.com");
        when(mailChannel.send(eq("m@test.com"), any(), any()))
                .thenReturn(new MailChannel.MailResult("FAILED", "connection refused"));

        NotificationService.SendOutcome outcome = service.sendMerchantNotification(
                "PAYMENT_SUCCEEDED", "evt-3", 1L, Map.of());

        verify(notificationMapper).insert(any(Notification.class));
        verify(notificationMapper).update(isNull(), any());
        assertEquals("FAILED", outcome.emailStatus());
    }

    @Test
    void duplicateEventReturnsNull() {
        when(templateMapper.selectOne(any())).thenReturn(TEMPLATE);
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("uk_event_id"));

        NotificationService.SendOutcome outcome = service.sendMerchantNotification(
                "PAYMENT_SUCCEEDED", "evt-dup", 1L, Map.of());

        assertNull(outcome);
        verify(mailChannel, never()).send(any(), any(), any());
    }

    @Test
    void missingTemplateThrows() {
        when(templateMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.sendMerchantNotification(
                "PAYMENT_SUCCEEDED", "evt-4", 1L, Map.of()));
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    /** 平台通知（对账异常等）：收件人 USER/平台公共信箱，邮件发配置的平台邮箱，不查商户。 */
    @Test
    void platformNotificationTargetsUserMailbox() {
        NotificationService service = new NotificationService(templateMapper, notificationMapper,
                new TemplateRenderer(), mailChannel, merchantService,
                new NotificationProperties(null, "ops@finflow.local"));
        when(templateMapper.selectOne(any())).thenReturn(TEMPLATE);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        when(mailChannel.send(eq("ops@finflow.local"), any(), any()))
                .thenReturn(new MailChannel.MailResult("SENT", null));

        NotificationService.SendOutcome outcome = service.sendPlatformNotification(
                "RECONCILIATION_DIFFERENCE", "evt-p1",
                Map.of("taskNo", "R1", "differenceType", "金额不一致",
                        "platformAmount", "100.00", "channelAmount", "99.00"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertEquals("USER", captor.getValue().getReceiverType());
        assertEquals(0L, captor.getValue().getReceiverId());
        assertEquals("SENT", outcome.emailStatus());
        verify(merchantService, never()).getDetail(any());
    }

    /** 平台邮箱未配置：站内信照发，邮件记 SKIPPED。 */
    @Test
    void platformNotificationSkipsEmailWithoutConfig() {
        when(templateMapper.selectOne(any())).thenReturn(TEMPLATE);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        when(mailChannel.send(isNull(), any(), any()))
                .thenReturn(new MailChannel.MailResult("SKIPPED", "收件人无邮箱"));

        NotificationService.SendOutcome outcome = service.sendPlatformNotification(
                "RECONCILIATION_DIFFERENCE", "evt-p2", Map.of());

        verify(notificationMapper).insert(any(Notification.class));
        assertEquals("SKIPPED", outcome.emailStatus());
    }

    private void stubMerchantEmail(String email) {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setContactEmail(email);
        when(merchantService.getDetail(1L))
                .thenReturn(new MerchantDetailResponse(MerchantResponse.from(merchant), List.of()));
    }

    private static NotificationTemplate template() {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(1L);
        template.setTemplateCode("PAYMENT_SUCCEEDED");
        template.setTitle("支付成功：订单 {{platformOrderNo}}");
        template.setBody("您的订单 {{platformOrderNo}} 已支付成功，金额 {{amount}} {{currency}}，支付时间 {{paidAt}}。");
        template.setEnabled(true);
        return template;
    }
}
