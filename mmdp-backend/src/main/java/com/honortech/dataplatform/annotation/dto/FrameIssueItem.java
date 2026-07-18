package com.honortech.dataplatform.annotation.dto;

/**
 * 帧级问题标注项，支持单帧和帧段两种模式
 */
public record FrameIssueItem(
        int frame,              // startFrame（单帧时即为帧号）
        Integer endFrame,       // 帧段结束帧，null 表示单帧标注
        String description,
        String severity,        // low/medium/high
        String category,        // 自由文本类别（向后兼容）
        String defectType       // MotionDB 缺陷类型枚举: jointJump/sliding/jointDeformity/floatPenetrate/displacementMissing/flatScene/temporalConsistency/testSet/other
) {}
