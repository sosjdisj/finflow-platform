package org.example.finflowbackend.system.file;

import org.example.finflowbackend.system.api.file.FileObjectApi;

/** 文件中心服务（S38）：跨模块消费方只经 {@link FileObjectApi} 调用，模块内部接口仅用于声明实现。 */
public interface FileObjectService extends FileObjectApi {
}
