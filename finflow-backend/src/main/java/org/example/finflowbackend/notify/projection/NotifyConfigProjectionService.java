package org.example.finflowbackend.notify.projection;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.contract.MerchantNotifyConfigPayload;
import org.example.finflowbackend.notify.entity.NotifyMerchantConfig;
import org.example.finflowbackend.notify.mapper.NotifyMerchantConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 商户通知配置投影写入（M16）：消费 merchant.notify-config.changed 事件后按商户 upsert。
 *
 * <p>幂等：Kafka 同 key（merchantId）同分区有序，重复投递按最后一次覆盖；
 * 用显式 UpdateWrapper 而非 updateById，保证「商户清空回调地址 / 邮箱」能把投影置空
 * （updateById 会忽略 null 字段，导致旧值残留）。</p>
 */
@Service
public class NotifyConfigProjectionService {

    private static final Logger log = LoggerFactory.getLogger(NotifyConfigProjectionService.class);

    private final NotifyMerchantConfigMapper configMapper;

    public NotifyConfigProjectionService(NotifyMerchantConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public void upsert(MerchantNotifyConfigPayload payload) {
        NotifyMerchantConfig existing = configMapper.selectOne(Wrappers.<NotifyMerchantConfig>query()
                .eq("merchant_id", payload.merchantId()));
        if (existing == null) {
            NotifyMerchantConfig config = new NotifyMerchantConfig();
            config.setMerchantId(payload.merchantId());
            config.setWebhookUrl(payload.webhookUrl());
            config.setContactEmail(payload.contactEmail());
            config.setSecretCipher(payload.secretCipher());
            config.setChangedAt(payload.changedAt());
            configMapper.insert(config);
        } else {
            configMapper.update(null, Wrappers.<NotifyMerchantConfig>update()
                    .eq("id", existing.getId())
                    .set("webhook_url", payload.webhookUrl())
                    .set("contact_email", payload.contactEmail())
                    .set("secret_cipher", payload.secretCipher())
                    .set("changed_at", payload.changedAt()));
        }
        log.info("[通知配置投影] 已更新: merchantId={}, changedAt={}", payload.merchantId(), payload.changedAt());
    }
}
