-- ============================================================
-- S38 文件中心：file_object 表（文件元数据，只增不改）+ 上传/下载权限点
-- 对象本体存 MinIO（bucket=finflow，应用启动时自动确保），表里只存 object_key 与元数据。
-- object_key 规则：{bizType小写}/{yyyyMMdd}/{uuid}{扩展名}，全局唯一。
-- ============================================================

CREATE TABLE file_object
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    object_key   VARCHAR(200)    NOT NULL COMMENT '对象键（MinIO 内唯一路径，与 reconciliation_task.file_path 等引用对齐）',
    bucket       VARCHAR(63)     NOT NULL COMMENT '存储桶名',
    file_name    VARCHAR(255)    NOT NULL COMMENT '原始文件名（下载时回显）',
    content_type VARCHAR(100)    NOT NULL COMMENT 'MIME 类型',
    size_bytes   BIGINT          NOT NULL COMMENT '文件大小（字节）',
    biz_type     VARCHAR(30)     NOT NULL COMMENT '业务类型：MERCHANT_CERT/RECONCILIATION/EXPORT/SETTLEMENT',
    biz_id       BIGINT UNSIGNED NULL COMMENT '关联业务 ID（如对账任务 ID，可空）',
    uploader_id  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上传人用户 ID（0=系统）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_object_biz (biz_type, biz_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件中心元数据（对象存 MinIO，只增不改不删行）';

-- 权限点（id 沿 V33 的 107 顺延）
INSERT INTO sys_permission (id, code, name, type)
VALUES (108, 'system:file:upload', '文件上传', 2),
       (109, 'system:file:download', '文件下载', 2);
