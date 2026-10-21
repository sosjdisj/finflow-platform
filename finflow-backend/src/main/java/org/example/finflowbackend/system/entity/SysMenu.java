package org.example.finflowbackend.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 菜单（表 sys_menu）。对应迁移脚本 V4__create_rbac_tables.sql。
 * 只负责展示结构（树形 / 标题 / 路径 / 图标 / 排序），权限语义全部在 sys_permission，
 * 通过 permissionId 引用（可空）控制可见性。
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenu {

    /** 类型：目录。 */
    public static final int TYPE_DIRECTORY = 1;
    /** 类型：菜单。 */
    public static final int TYPE_PAGE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单 ID，0 表示根节点。 */
    private Long parentId;

    private String title;

    /** 前端路由路径。 */
    private String path;

    /** 图标名。 */
    private String icon;

    /** 类型：1 目录 / 2 菜单。 */
    private Integer menuType;

    /** 排序号，小在前。 */
    private Integer sortNo;

    /** 关联权限点（控制可见性），可空。 */
    private Long permissionId;

    /** 是否显示：1 是 / 0 隐藏。 */
    private Integer visible;

    /** 1 启用 / 0 禁用。 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
