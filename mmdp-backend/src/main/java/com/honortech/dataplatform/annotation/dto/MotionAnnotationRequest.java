package com.honortech.dataplatform.annotation.dto;

import java.util.List;
import java.util.Map;

/**
 * 标注保存/更新请求
 */
public record MotionAnnotationRequest(
        String status,
        String qualityRating,
        List<String> motionTags,
        Map<String, String> motiondbDefects,
        List<FrameIssueItem> frameIssues,
        List<String> textDescriptions,
        String overallComment,
        Integer version
) {}
