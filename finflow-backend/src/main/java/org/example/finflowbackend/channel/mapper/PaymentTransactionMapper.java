package org.example.finflowbackend.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.channel.entity.PaymentTransaction;

@Mapper
public interface PaymentTransactionMapper extends BaseMapper<PaymentTransaction> {
}
