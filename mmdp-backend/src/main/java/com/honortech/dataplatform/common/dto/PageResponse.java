package com.honortech.dataplatform.common.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public record PageResponse<T>(List<T> records, long total, long page, long pageSize) {

    public static <T> PageResponse<T> of(IPage<?> page, List<T> records) {
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
