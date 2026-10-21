package org.example.finflowbackend.clearing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.clearing.entity.ClearingDetail;

@Mapper
public interface ClearingDetailMapper extends BaseMapper<ClearingDetail> {
}
