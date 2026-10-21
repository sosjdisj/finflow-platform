package org.example.finflowbackend.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色-权限关联（表 sys_role_permission）。纯关联表，只增删不改。
 * 内置超级管理员不走本表，授权时统一查全量启用权限。
 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createdAt;
}
