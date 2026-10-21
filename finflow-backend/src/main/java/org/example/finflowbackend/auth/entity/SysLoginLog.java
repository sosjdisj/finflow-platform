package org.example.finflowbackend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录日志（表 sys_login_log）。审计表只增不改：成功与失败各记一条，朴素写法。
 * 对应迁移脚本 V5__add_login_log_and_device_info.sql。
 */
@Getter
@Setter
@TableName("sys_login_log")
public class SysLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 尝试登录的用户名（无论用户是否存在）。 */
    private String username;

    /** 登录成功时的用户 ID，失败为空。 */
    private Long userId;

    /** 1 成功 0 失败。 */
    private Integer success;

    /** 失败原因，成功为空。 */
    private String failReason;

    private String ip;

    private String userAgent;

    private LocalDateTime createdAt;
}
