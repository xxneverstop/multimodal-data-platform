package com.honortech.dataplatform.session.dto;

import com.honortech.dataplatform.asset.dto.DataAssetResponse;

import java.time.LocalDateTime;
import java.util.List;

public record SessionResponse(
        Long id,
        Long taskId,
        String taskName,
        String sessionId,
        String subjectCode,
        String actionName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        String uploadStatus,
        LocalDateTime createdAt,
        List<DataAssetResponse> assets
) {
}
