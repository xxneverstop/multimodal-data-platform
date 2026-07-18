package com.honortech.dataplatform.annotation.dto;

import java.util.Map;

/**
 * 标注进度统计响应
 */
public record AnnotationProgressResponse(
        int totalAssets,
        int annotatedCount,
        int inProgressCount,
        int unannotatedCount,
        Map<String, Integer> ratingDistribution
) {}
