package org.example.finflowbackend.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.audit.entity.AuditLog;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
