package org.example.finflowbackend.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.risk.entity.RiskBlacklist;
import org.example.finflowbackend.risk.mapper.RiskBlacklistMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 黑名单管理（S30）：用户 / IP / 设备三维度，维度 + 值唯一；删除即解除拉黑。
 */
@Service
public class RiskBlacklistService {

    private final RiskBlacklistMapper blacklistMapper;

    public RiskBlacklistService(RiskBlacklistMapper blacklistMapper) {
        this.blacklistMapper = blacklistMapper;
    }

    @Transactional
    public RiskBlacklist create(RiskBlacklist entry, String operator) {
        entry.setId(null);
        entry.setCreatedBy(operator);
        try {
            blacklistMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(RiskErrorCode.BLACKLIST_DUPLICATE);
        }
        return entry;
    }

    @Transactional
    public void delete(Long id) {
        if (blacklistMapper.deleteById(id) == 0) {
            throw new BusinessException(RiskErrorCode.BLACKLIST_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskBlacklist> page(long page, long size, String dimension, String value) {
        Page<RiskBlacklist> result = blacklistMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RiskBlacklist>()
                        .eq(dimension != null && !dimension.isBlank(), RiskBlacklist::getDimension, dimension)
                        .like(value != null && !value.isBlank(), RiskBlacklist::getValue, value)
                        .orderByDesc(RiskBlacklist::getId));
        return PageResponse.of(result);
    }
}
