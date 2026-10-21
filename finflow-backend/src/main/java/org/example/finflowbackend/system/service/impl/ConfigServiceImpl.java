package org.example.finflowbackend.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.entity.SystemConfig;
import org.example.finflowbackend.system.mapper.SystemConfigMapper;
import org.example.finflowbackend.system.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 系统配置读取实现（S37）。Cache-Aside：读 → Redis（TTL 10 分钟兜底）→ 库 → 定义默认值；
 * 修改后由管理端删缓存键即完成刷新，多实例下次读自然加载新值（不做动态推送）。
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigServiceImpl.class);

    /** 缓存键前缀 + TTL 兜底：即使删缓存失败，最多 10 分钟后也会读到新值。 */
    static final String CACHE_KEY_PREFIX = "finflow:config:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SystemConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;

    public ConfigServiceImpl(SystemConfigMapper configMapper, StringRedisTemplate redisTemplate) {
        this.configMapper = configMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getString(ConfigKeys.Def def) {
        String cacheKey = CACHE_KEY_PREFIX + def.key();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        SystemConfig config = configMapper.selectOne(
                Wrappers.<SystemConfig>query().eq("config_key", def.key()));
        if (config == null) {
            // 无行兜底定义默认值（seed 保证有行；此处防御手工删数据），不写缓存便于恢复后即读库
            log.warn("[系统配置] 配置项缺行，回退默认值: key={}", def.key());
            return def.defaultValue();
        }
        redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(), CACHE_TTL);
        return config.getConfigValue();
    }

    @Override
    public long getLong(ConfigKeys.Def def) {
        try {
            return Long.parseLong(getString(def));
        } catch (NumberFormatException e) {
            log.warn("[系统配置] 数值解析失败，回退默认值: key={}, value={}", def.key(), getString(def));
            return Long.parseLong(def.defaultValue());
        }
    }

    @Override
    public boolean getBool(ConfigKeys.Def def) {
        return "true".equalsIgnoreCase(getString(def)) || "1".equals(getString(def));
    }
}
