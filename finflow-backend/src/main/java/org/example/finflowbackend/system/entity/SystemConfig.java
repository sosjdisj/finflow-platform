package org.example.finflowbackend.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置（表 system_config，S37）。key 与 ConfigKeys 定义一一对应；
 * 值统一字符串存储，按 valueType 解析。无逻辑删除，改值即覆盖。
 */
@Getter
@Setter
@TableName("system_config")
public class SystemConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键（唯一）。 */
    private String configKey;

    /** 配置值（字符串存储）。 */
    private String configValue;

    /** 值类型：NUMBER/STRING/BOOLEAN/JSON。 */
    private String valueType;

    /** 配置说明。 */
    private String description;

    /** 最后修改人用户 ID（0=系统初始化）。 */
    private Long updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
