package org.example.finflowbackend.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.merchant.entity.Merchant;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
