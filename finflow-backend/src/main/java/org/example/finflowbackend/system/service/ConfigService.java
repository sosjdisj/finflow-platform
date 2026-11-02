package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.api.ConfigApi;

/**
 * 系统配置统一读取入口（S37）。跨模块消费方只经 {@link ConfigApi} 调用（铁律 4），
 * 模块内部服务接口仅用于声明实现；传 {@code ConfigKeys.Def} 保证读取点不需要各自写兜底。
 */
public interface ConfigService extends ConfigApi {
}
