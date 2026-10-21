package org.example.finflowbackend.system.file;

import java.time.LocalDateTime;

/** 文件元数据响应（S38）。 */
public record FileObjectResponse(long id, String objectKey, String fileName, String contentType,
                                 long sizeBytes, String bizType, Long bizId, long uploaderId,
                                 LocalDateTime createdAt) {

    public static FileObjectResponse from(FileObject file) {
        return new FileObjectResponse(file.getId(), file.getObjectKey(), file.getFileName(),
                file.getContentType(), file.getSizeBytes(), file.getBizType(), file.getBizId(),
                file.getUploaderId(), file.getCreatedAt());
    }
}
