package org.example.finflowbackend.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.refund.entity.RefundOrderStateLog;

/**
 * 退款状态留痕 Mapper，只增不改。
 */
@Mapper
public interface RefundOrderStateLogMapper extends BaseMapper<RefundOrderStateLog> {
}