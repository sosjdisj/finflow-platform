package org.example.finflowbackend.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色（表 sys_role）。对应迁移脚本 V4__create_rbac_tables.sql。
 * 内置角色（builtIn = 1）禁止删除与修改。
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码，如 SUPER_ADMIN。 */
    private String code;

    private String name;

    private String description;

    /** 内置角色：1 是（禁删禁改）/ 0 否。 */
    private Integer builtIn;

    /** 1 启用 / 0 禁用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
