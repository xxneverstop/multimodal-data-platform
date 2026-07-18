package com.honortech.dataplatform.annotation.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 标注信息响应
 */
public record MotionAnnotationResponse(
        Long id,
        Long assetId,
        String status,
        String qualityRating,
        List<String> motionTags,
        Map<String, String> motiondbDefects,
        List<FrameIssueItem> frameIssues,
        List<String> textDescriptions,
        String overallComment,
        Long annotatorId,
        String annotatorName,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
