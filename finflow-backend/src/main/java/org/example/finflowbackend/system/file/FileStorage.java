package org.example.finflowbackend.system.file;

import java.io.InputStream;

/**
 * 对象存储抽象（S38）：文件中心的存储端口，业务方（对账等）只依赖本接口，
 * 不感知 MinIO 细节（铁律 4：跨模块只依赖暴露的接口）。
 * 对象只增不改：同 key 重复 put 视为异常路径，不做覆盖语义。
 */
public interface FileStorage {

    /** 上传对象：objectKey 全局唯一（调用方保证），in 由调用方负责关闭。 */
    void put(String objectKey, InputStream in, long size, String contentType);

    /** 打开对象读取流：调用方负责关闭。对象不存在抛 FILE_STORE_FAILED。 */
    InputStream get(String objectKey);
}
