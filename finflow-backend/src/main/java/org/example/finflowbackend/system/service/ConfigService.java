package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.config.ConfigKeys;

/**
 * 系统配置统一读取入口（S37）。跨模块消费方只依赖本接口（铁律 4）。
 * 传 {@link ConfigKeys.Def}（key + 默认值一体）保证读取点不需要各自写兜底；
 * 值一律字符串存储，按语义解析，解析失败回退默认值（坏值不阻断业务）。
 */
public interface ConfigService {

    /** 读原始字符串值：Redis 缓存 → 库 → 定义默认值。 */
    String getString(ConfigKeys.Def def);

    /** 读整数值（NUMBER 型）。 */
    long getLong(ConfigKeys.Def def);

    /** 读布尔值（BOOLEAN 型："true"/"1" 为真）。 */
    boolean getBool(ConfigKeys.Def def);
}
