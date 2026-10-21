package org.example.finflowbackend.refund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.refund.entity.RefundOrder;

/**
 * 退款单 Mapper。防超退的行锁在支付订单上（PaymentOrderMapper.selectForUpdate）；
 * 本表的 selectForUpdate 用于状态推进：FOR UPDATE 当前读拿到最新已提交状态，避免
 * 事务快照读到过期状态后按旧状态做非法转换（S21 渠道回调与受理竞态的实际案例）。
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    /** 行级锁当前读：返回最新已提交的退款单。 */
    RefundOrder selectForUpdate(@Param("id") Long id);
}