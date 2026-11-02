package org.example.finflowbackend.notify.api;

import java.util.Optional;

/**
 * 商户通知配置读取能力（M16 facade）。
 *
 * <p>两种实现按 {@code finflow.notify.provider} 切换：backend 进程读 merchant / api 模块（回退路径），
 * 通知服务进程读自己的 notify_merchant_config 投影。调用方只面向本接口，不感知部署形态。</p>
 */
public interface NotifyMerchantConfigApi {

    /** 商户通知配置；商户不存在或投影尚未到达时返回 empty。 */
    Optional<NotifyConfig> find(Long merchantId);
}
