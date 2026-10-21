package org.example.finflowbackend.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 刷新令牌（表 sys_refresh_token）。只存哈希，吊销写 revokedAt。
 * 对应迁移脚本 V3__create_sys_refresh_token.sql。
 */
@Getter
@Setter
@TableName("sys_refresh_token")
public class SysRefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 令牌明文的 SHA-256 十六进制哈希。 */
    private String tokenHash;

    /** 签发时的设备名（User-Agent 截断），设备列表展示用。 */
    private String deviceName;

    /** 签发时的客户端 IP。 */
    private String ip;

    private LocalDateTime expiresAt;

    /** 空表示有效。 */
    private LocalDateTime revokedAt;

    private LocalDateTime createdAt;
}
