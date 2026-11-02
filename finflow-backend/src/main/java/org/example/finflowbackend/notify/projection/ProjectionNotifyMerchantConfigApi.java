package org.example.finflowbackend.notify.projection;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.util.AesCipher;
import org.example.finflowbackend.notify.api.NotifyConfig;
import org.example.finflowbackend.notify.api.NotifyMerchantConfigApi;
import org.example.finflowbackend.notify.entity.NotifyMerchantConfig;
import org.example.finflowbackend.notify.mapper.NotifyMerchantConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 通知服务进程内实现（M16）：只读自己的 notify_merchant_config 投影，不发起任何同步调用。
 * 密钥密文由 backend 事件投影带过来，这里用共享的 {@code finflow.security.secret-encrypt-key} 解密。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.provider", havingValue = "true")
public class ProjectionNotifyMerchantConfigApi implements NotifyMerchantConfigApi {

    private final NotifyMerchantConfigMapper configMapper;
    private final String secretEncryptKey;

    public ProjectionNotifyMerchantConfigApi(NotifyMerchantConfigMapper configMapper,
                                             @Value("${finflow.security.secret-encrypt-key}") String secretEncryptKey) {
        this.configMapper = configMapper;
        this.secretEncryptKey = secretEncryptKey;
    }

    @Override
    public Optional<NotifyConfig> find(Long merchantId) {
        NotifyMerchantConfig config = configMapper.selectOne(Wrappers.<NotifyMerchantConfig>query()
                .eq("merchant_id", merchantId));
        if (config == null) {
            return Optional.empty();
        }
        String secret = config.getSecretCipher() == null
                ? null
                : AesCipher.decrypt(config.getSecretCipher(), secretEncryptKey);
        return Optional.of(new NotifyConfig(config.getWebhookUrl(), config.getContactEmail(), secret));
    }
}
