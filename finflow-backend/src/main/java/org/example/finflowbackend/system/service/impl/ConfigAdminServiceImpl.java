package org.example.finflowbackend.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.audit.api.AuditApi;
import org.example.finflowbackend.common.context.CurrentUser;
import org.example.finflowbackend.common.context.CurrentUserHolder;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.api.ConfigKeys;
import org.example.finflowbackend.system.dto.ConfigResponse;
import org.example.finflowbackend.system.dto.ConfigUpdateRequest;
import org.example.finflowbackend.system.entity.SystemConfig;
import org.example.finflowbackend.system.mapper.SystemConfigMapper;
import org.example.finflowbackend.system.service.ConfigAdminService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统配置管理端实现（S37）：修改即审计（@Audited 在 Controller 层织入，快照在此提交）。
 * key 不可增删（集合固定在 ConfigKeys），仅允许改值与描述，防止配置漂移。
 */
@Service
public class ConfigAdminServiceImpl implements ConfigAdminService {

    private final SystemConfigMapper configMapper;
    private final StringRedisTemplate redisTemplate;
    private final AuditApi auditApi;

    public ConfigAdminServiceImpl(SystemConfigMapper configMapper, StringRedisTemplate redisTemplate,
                                  AuditApi auditApi) {
        this.configMapper = configMapper;
        this.redisTemplate = redisTemplate;
        this.auditApi = auditApi;
    }

    @Override
    public List<ConfigResponse> listAll() {
        // 只展示仍在 ConfigKeys 登记的项：历史遗留行（如 M14 已迁往 Nacos 的风控阈值）不再暴露，
        // 防止「后台可改但运行时已不读」的假事实源
        return configMapper.selectList(Wrappers.<SystemConfig>query().orderByAsc("config_key"))
                .stream()
                .filter(config -> ConfigKeys.of(config.getConfigKey()).isPresent())
                .map(ConfigResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ConfigResponse update(String key, ConfigUpdateRequest request) {
        ConfigKeys.Def def = ConfigKeys.of(key)
                .orElseThrow(() -> new BusinessException(SystemErrorCode.CONFIG_KEY_NOT_FOUND));
        String valueError = ConfigKeys.validateValue(def, request.configValue());
        if (valueError != null) {
            throw new BusinessException(SystemErrorCode.CONFIG_VALUE_INVALID, def.key() + " " + valueError);
        }

        SystemConfig config = configMapper.selectOne(
                Wrappers.<SystemConfig>query().eq("config_key", key));
        if (config == null) {
            throw new BusinessException(SystemErrorCode.CONFIG_KEY_NOT_FOUND);
        }
        SystemConfig before = snapshot(config);
        config.setConfigValue(request.configValue());
        if (request.description() != null && !request.description().isBlank()) {
            config.setDescription(request.description());
        }
        CurrentUser actor = CurrentUserHolder.current();
        config.setUpdatedBy(actor == null ? 0L : actor.userId());
        configMapper.updateById(config);

        auditApi.submit(before, snapshot(config));
        // 刷新 = 删缓存：即使删除失败，TTL 兜底最多延迟 10 分钟
        redisTemplate.delete(ConfigServiceImpl.CACHE_KEY_PREFIX + key);
        return ConfigResponse.from(config);
    }

    private SystemConfig snapshot(SystemConfig config) {
        SystemConfig copy = new SystemConfig();
        copy.setId(config.getId());
        copy.setConfigKey(config.getConfigKey());
        copy.setConfigValue(config.getConfigValue());
        copy.setValueType(config.getValueType());
        copy.setDescription(config.getDescription());
        copy.setUpdatedBy(config.getUpdatedBy());
        return copy;
    }
}
