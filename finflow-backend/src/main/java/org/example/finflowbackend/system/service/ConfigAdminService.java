package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.dto.ConfigResponse;
import org.example.finflowbackend.system.dto.ConfigUpdateRequest;

import java.util.List;

/** 系统配置管理端服务（S37）：列表与修改，仅 ConfigController 使用。 */
public interface ConfigAdminService {

    /** 全量配置项列表（按 key 排序）。 */
    List<ConfigResponse> listAll();

    /** 修改配置值/描述：校验值合法 → 落库 → 删缓存（刷新 = 失效重读）。 */
    ConfigResponse update(String key, ConfigUpdateRequest request);
}
