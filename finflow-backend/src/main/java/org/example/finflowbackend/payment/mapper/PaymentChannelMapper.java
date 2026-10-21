package org.example.finflowbackend.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.payment.entity.PaymentChannel;

@Mapper
public interface PaymentChannelMapper extends BaseMapper<PaymentChannel> {
}
