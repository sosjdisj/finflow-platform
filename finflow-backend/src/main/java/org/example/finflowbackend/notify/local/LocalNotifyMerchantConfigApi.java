package org.example.finflowbackend.notify.local;

import org.example.finflowbackend.merchant.api.ApiKeyApi;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.merchant.api.MerchantResponse;
import org.example.finflowbackend.notify.api.NotifyConfig;
import org.example.finflowbackend.notify.api.NotifyMerchantConfigApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * backend 进程内实现（M16 回退路径）：直接读 merchant / api 模块的 facade，
 * 与拆分前行为一致（{@code finflow.notify.provider=false}，缺省即本地）。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.provider", havingValue = "false", matchIfMissing = true)
public class LocalNotifyMerchantConfigApi implements NotifyMerchantConfigApi {

    private final MerchantApi merchantApi;
    private final ApiKeyApi apiKeyApi;

    public LocalNotifyMerchantConfigApi(MerchantApi merchantApi, ApiKeyApi apiKeyApi) {
        this.merchantApi = merchantApi;
        this.apiKeyApi = apiKeyApi;
    }

    @Override
    public Optional<NotifyConfig> find(Long merchantId) {
        MerchantResponse merchant = merchantApi.getDetail(merchantId).merchant();
        if (merchant == null) {
            return Optional.empty();
        }
        String secret = apiKeyApi.findSigningSecret(merchantId).orElse(null);
        return Optional.of(new NotifyConfig(merchant.webhookUrl(), merchant.contactEmail(), secret));
    }
}
