package org.example.finflowbackend.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 统一分页响应：只暴露记录与总数，屏蔽 MP Page 的内部分页字段。
 * 所有分页接口一律返回该结构。
 */
public record PageResponse<T>(List<T> records, long total) {

    public static <T> PageResponse<T> of(IPage<T> page) {
        return new PageResponse<>(page.getRecords(), page.getTotal());
    }
}
