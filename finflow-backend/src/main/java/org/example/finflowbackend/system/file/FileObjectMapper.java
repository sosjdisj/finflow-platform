package org.example.finflowbackend.system.file;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 文件元数据 Mapper：简单 CRUD 走 BaseMapper。 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {
}
