package org.example.finflowbackend.payment.api;

/**
 * 渠道回调业务入口（对外能力）：由 channel 模块收到渠道异步通知后调用。
 * 平台订单不存在返回 false；重复通知按幂等返回 true。
 */
public interface ChannelCallbackApi {

    boolean handle(ChannelCallbackCommand command);
}
