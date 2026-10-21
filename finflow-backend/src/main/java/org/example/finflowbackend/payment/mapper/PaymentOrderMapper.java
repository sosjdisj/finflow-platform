package org.example.finflowbackend.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.payment.entity.PaymentOrder;

/**
 * 支付订单 Mapper。行锁读走手写 XML（S21 退款防超退），其余简单 CRUD 用 BaseMapper。
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    /** 按 ID 行锁读（select ... for update），退款占用/释放可退额度前必须先走这里。 */
    PaymentOrder selectForUpdate(@Param("id") Long id);
}