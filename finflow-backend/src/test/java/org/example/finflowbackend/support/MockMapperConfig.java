package org.example.finflowbackend.support;

import org.example.finflowbackend.account.mapper.AccountMapper;
import org.example.finflowbackend.auth.mapper.SysLoginLogMapper;
import org.example.finflowbackend.auth.mapper.SysRefreshTokenMapper;
import org.example.finflowbackend.auth.mapper.SysUserMapper;
import org.example.finflowbackend.ledger.mapper.AccountLedgerMapper;
import org.example.finflowbackend.merchant.mapper.MerchantApiKeyMapper;
import org.example.finflowbackend.merchant.mapper.MerchantMapper;
import org.example.finflowbackend.merchant.mapper.MerchantRateLogMapper;
import org.example.finflowbackend.merchant.mapper.MerchantRateMapper;
import org.example.finflowbackend.merchant.mapper.MerchantSettleAccountMapper;
import org.example.finflowbackend.merchant.mapper.MerchantStatusLogMapper;
import org.example.finflowbackend.payment.mapper.PaymentCallbackMapper;
import org.example.finflowbackend.payment.mapper.PaymentChannelMapper;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.mapper.PaymentOrderStateLogMapper;
import org.example.finflowbackend.payment.mapper.PaymentTransactionMapper;
import org.example.finflowbackend.refund.mapper.RefundOrderMapper;
import org.example.finflowbackend.refund.mapper.RefundOrderStateLogMapper;
import org.example.finflowbackend.clearing.mapper.ClearingDetailMapper;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.merchant.mapper.MerchantTierRateMapper;
import org.example.finflowbackend.settlement.mapper.SettlementDetailMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderStateLogMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationBillItemMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationMatchMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationDifferenceMapper;
import org.example.finflowbackend.system.mapper.SysMenuMapper;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.mapper.SysRolePermissionMapper;
import org.example.finflowbackend.system.mapper.SysUserRoleMapper;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 测试用 Mapper 替身。
 * test profile 排除了 DataSourceAutoConfiguration，MyBatis-Plus 也随之退避、没有真实 Mapper Bean，
 * 而 Service 依赖它们，所以在需要 Spring 上下文的测试里统一提供 Mock 替身。
 * S39 引入 Testcontainers 后删除本类。
 */
@TestConfiguration
public class MockMapperConfig {

    @Bean
    public SysUserMapper sysUserMapper() {
        return Mockito.mock(SysUserMapper.class);
    }

    @Bean
    public SysRefreshTokenMapper sysRefreshTokenMapper() {
        return Mockito.mock(SysRefreshTokenMapper.class);
    }

    @Bean
    public SysLoginLogMapper sysLoginLogMapper() {
        return Mockito.mock(SysLoginLogMapper.class);
    }

    @Bean
    public SysRoleMapper sysRoleMapper() {
        return Mockito.mock(SysRoleMapper.class);
    }

    @Bean
    public SysPermissionMapper sysPermissionMapper() {
        return Mockito.mock(SysPermissionMapper.class);
    }

    @Bean
    public SysMenuMapper sysMenuMapper() {
        return Mockito.mock(SysMenuMapper.class);
    }

    @Bean
    public SysUserRoleMapper sysUserRoleMapper() {
        return Mockito.mock(SysUserRoleMapper.class);
    }

    @Bean
    public SysRolePermissionMapper sysRolePermissionMapper() {
        return Mockito.mock(SysRolePermissionMapper.class);
    }

    @Bean
    public MerchantMapper merchantMapper() {
        return Mockito.mock(MerchantMapper.class);
    }

    @Bean
    public MerchantStatusLogMapper merchantStatusLogMapper() {
        return Mockito.mock(MerchantStatusLogMapper.class);
    }

    @Bean
    public MerchantApiKeyMapper merchantApiKeyMapper() {
        return Mockito.mock(MerchantApiKeyMapper.class);
    }

    @Bean
    public MerchantRateMapper merchantRateMapper() {
        return Mockito.mock(MerchantRateMapper.class);
    }

    @Bean
    public MerchantRateLogMapper merchantRateLogMapper() {
        return Mockito.mock(MerchantRateLogMapper.class);
    }

    @Bean
    public MerchantSettleAccountMapper merchantSettleAccountMapper() {
        return Mockito.mock(MerchantSettleAccountMapper.class);
    }

    @Bean
    public AccountMapper accountMapper() {
        return Mockito.mock(AccountMapper.class);
    }

    @Bean
    public AccountLedgerMapper accountLedgerMapper() {
        return Mockito.mock(AccountLedgerMapper.class);
    }

    @Bean
    public PaymentOrderMapper paymentOrderMapper() {
        return Mockito.mock(PaymentOrderMapper.class);
    }

    @Bean
    public PaymentOrderStateLogMapper paymentOrderStateLogMapper() {
        return Mockito.mock(PaymentOrderStateLogMapper.class);
    }

    @Bean
    public PaymentChannelMapper paymentChannelMapper() {
        return Mockito.mock(PaymentChannelMapper.class);
    }

    @Bean
    public PaymentTransactionMapper paymentTransactionMapper() {
        return Mockito.mock(PaymentTransactionMapper.class);
    }

    @Bean
    public PaymentCallbackMapper paymentCallbackMapper() {
        return Mockito.mock(PaymentCallbackMapper.class);
    }

    @Bean
    public RefundOrderMapper refundOrderMapper() {
        return Mockito.mock(RefundOrderMapper.class);
    }

    @Bean
    public RefundOrderStateLogMapper refundOrderStateLogMapper() {
        return Mockito.mock(RefundOrderStateLogMapper.class);
    }

    @Bean
    public ClearingOrderMapper clearingOrderMapper() {
        return Mockito.mock(ClearingOrderMapper.class);
    }

    @Bean
    public ClearingDetailMapper clearingDetailMapper() {
        return Mockito.mock(ClearingDetailMapper.class);
    }

    @Bean
    public MerchantTierRateMapper merchantTierRateMapper() {
        return Mockito.mock(MerchantTierRateMapper.class);
    }

    @Bean
    public SettlementOrderMapper settlementOrderMapper() {
        return Mockito.mock(SettlementOrderMapper.class);
    }

    @Bean
    public SettlementDetailMapper settlementDetailMapper() {
        return Mockito.mock(SettlementDetailMapper.class);
    }

    @Bean
    public SettlementOrderStateLogMapper settlementOrderStateLogMapper() {
        return Mockito.mock(SettlementOrderStateLogMapper.class);
    }

    @Bean
    public ReconciliationTaskMapper reconciliationTaskMapper() {
        return Mockito.mock(ReconciliationTaskMapper.class);
    }

    @Bean
    public ReconciliationBillItemMapper reconciliationBillItemMapper() {
        return Mockito.mock(ReconciliationBillItemMapper.class);
    }

    @Bean
    public ReconciliationMatchMapper reconciliationMatchMapper() {
        return Mockito.mock(ReconciliationMatchMapper.class);
    }

    @Bean
    public ReconciliationRecordMapper reconciliationRecordMapper() {
        return Mockito.mock(ReconciliationRecordMapper.class);
    }

    @Bean
    public ReconciliationDifferenceMapper reconciliationDifferenceMapper() {
        return Mockito.mock(ReconciliationDifferenceMapper.class);
    }

    @Bean
    public org.example.finflowbackend.risk.mapper.RiskRuleMapper riskRuleMapper() {
        return Mockito.mock(org.example.finflowbackend.risk.mapper.RiskRuleMapper.class);
    }

    @Bean
    public org.example.finflowbackend.risk.mapper.RiskEventMapper riskEventMapper() {
        return Mockito.mock(org.example.finflowbackend.risk.mapper.RiskEventMapper.class);
    }

    @Bean
    public org.example.finflowbackend.risk.mapper.RiskBlacklistMapper riskBlacklistMapper() {
        return Mockito.mock(org.example.finflowbackend.risk.mapper.RiskBlacklistMapper.class);
    }

    @Bean
    public org.example.finflowbackend.webhook.mapper.WebhookEventMapper webhookEventMapper() {
        return Mockito.mock(org.example.finflowbackend.webhook.mapper.WebhookEventMapper.class);
    }

    @Bean
    public org.example.finflowbackend.webhook.mapper.WebhookDeliveryMapper webhookDeliveryMapper() {
        return Mockito.mock(org.example.finflowbackend.webhook.mapper.WebhookDeliveryMapper.class);
    }

    @Bean
    public org.example.finflowbackend.notification.mapper.NotificationMapper notificationMapper() {
        return Mockito.mock(org.example.finflowbackend.notification.mapper.NotificationMapper.class);
    }

    @Bean
    public org.example.finflowbackend.notification.mapper.NotificationTemplateMapper notificationTemplateMapper() {
        return Mockito.mock(org.example.finflowbackend.notification.mapper.NotificationTemplateMapper.class);
    }

    @Bean
    public org.example.finflowbackend.audit.mapper.AuditLogMapper auditLogMapper() {
        return Mockito.mock(org.example.finflowbackend.audit.mapper.AuditLogMapper.class);
    }

    @Bean
    public org.example.finflowbackend.system.mapper.DashboardStatsMapper dashboardStatsMapper() {
        return Mockito.mock(org.example.finflowbackend.system.mapper.DashboardStatsMapper.class);
    }

    // S37 配置中心 / S38 文件中心
    @Bean
    public org.example.finflowbackend.system.mapper.SystemConfigMapper systemConfigMapper() {
        return Mockito.mock(org.example.finflowbackend.system.mapper.SystemConfigMapper.class);
    }

    @Bean
    public org.example.finflowbackend.system.file.FileObjectMapper fileObjectMapper() {
        return Mockito.mock(org.example.finflowbackend.system.file.FileObjectMapper.class);
    }
}
