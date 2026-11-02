package org.example.finflowbackend.merchant.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.contract.DomainEvent;
import org.example.finflowbackend.contract.EventTopics;
import org.example.finflowbackend.contract.EventTypes;
import org.example.finflowbackend.contract.MerchantNotifyConfigPayload;
import org.example.finflowbackend.merchant.entity.Merchant;
import org.example.finflowbackend.merchant.entity.MerchantApiKey;
import org.example.finflowbackend.merchant.mapper.MerchantApiKeyMapper;
import org.example.finflowbackend.merchant.mapper.MerchantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 商户通知配置事件发布（M16）：商户资料（回调地址 / 邮箱）或 API 凭证变更后，
 * 发布一份配置快照给通知服务落投影。密钥只发 AES-GCM 密文，明文不出 merchant 模块。
 *
 * <p>发布动作挂在事务 afterCommit 上（见 {@link DomainEventPublisher}），
 * 事务回滚时不会产生通知服务读不到的脏投影。</p>
 */
@Component
public class MerchantNotifyConfigPublisher {

    private static final Logger log = LoggerFactory.getLogger(MerchantNotifyConfigPublisher.class);

    private final MerchantMapper merchantMapper;
    private final MerchantApiKeyMapper apiKeyMapper;
    private final DomainEventPublisher eventPublisher;

    public MerchantNotifyConfigPublisher(MerchantMapper merchantMapper, MerchantApiKeyMapper apiKeyMapper,
                                         DomainEventPublisher eventPublisher) {
        this.merchantMapper = merchantMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 发布商户当前通知配置快照；商户不存在（已被删除等）时静默跳过。 */
    public void publish(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            log.warn("[通知配置] 商户不存在，跳过配置事件发布: merchantId={}", merchantId);
            return;
        }
        MerchantApiKey latestKey = apiKeyMapper.selectOne(Wrappers.<MerchantApiKey>query()
                .eq("merchant_id", merchantId).orderByDesc("id").last("LIMIT 1"));
        String secretCipher = latestKey == null ? null : latestKey.getSecretCipher();
        eventPublisher.publish(EventTopics.MERCHANT_NOTIFY_CONFIG, String.valueOf(merchantId),
                DomainEvent.of(EventTypes.MERCHANT_NOTIFY_CONFIG_CHANGED,
                        new MerchantNotifyConfigPayload(merchantId, merchant.getWebhookUrl(),
                                merchant.getContactEmail(), secretCipher, LocalDateTime.now())));
    }
}
