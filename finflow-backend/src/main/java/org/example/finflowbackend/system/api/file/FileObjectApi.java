package org.example.finflowbackend.system.api.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/** 文件中心对外能力（S38，M03 facade）：跨模块（对账、结算等）上传与读取文件。 */
public interface FileObjectApi {

    /**
     * 上传：校验 → 生成 objectKey → 存对象 → 落元数据（同事务边界由调用方把握；
     * 对象先落存储，元数据落库失败仅残留孤儿对象，可接受）。
     *
     * @param uploaderId 上传人用户 ID（0=系统；由调用方显式传入，保持服务无登录态依赖）
     */
    FileObjectResponse upload(MultipartFile file, String bizType, Long bizId, long uploaderId);

    /** 元数据查询；不存在抛 FILE_NOT_FOUND。 */
    FileObjectResponse getMeta(long id);

    /** 打开对象流（调用方负责关闭）；元数据不存在抛 FILE_NOT_FOUND。 */
    InputStream openStream(long id);

    /** 按 objectKey 打开对象流（调用方负责关闭）；供已保存 objectKey 的业务方回读。 */
    InputStream openByObjectKey(String objectKey);
}
