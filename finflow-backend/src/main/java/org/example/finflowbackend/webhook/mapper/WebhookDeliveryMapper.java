package org.example.finflowbackend.webhook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.webhook.entity.WebhookDelivery;

@Mapper
public interface WebhookDeliveryMapper extends BaseMapper<WebhookDelivery> {
}
