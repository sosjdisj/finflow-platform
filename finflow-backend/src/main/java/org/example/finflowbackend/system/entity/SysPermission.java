package org.example.finflowbackend.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 权限点（表 sys_permission）。对应迁移脚本 V4__create_rbac_tables.sql。
 * code 是接口鉴权与菜单可见性的唯一事实源，创建后不允许修改。
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission {

    /** 类型：菜单可见性。 */
    public static final int TYPE_MENU = 1;
    /** 类型：操作接口。 */
    public static final int TYPE_API = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码，如 system:role:list，对应方法级权限注解。 */
    private String code;

    private String name;

    /** 类型：1 菜单可见性 / 2 操作接口。 */
    private Integer type;

    private String description;

    /** 1 启用 / 0 禁用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
