package com.honortech.dataplatform.session.dto;

import com.honortech.dataplatform.asset.dto.DataAssetResponse;

import java.time.LocalDateTime;
import java.util.List;

public record SessionResponse(
        Long id,
        String sessionCode,
        Long taskId,
        String taskName,
        String sessionId,
        String localSessionId,
        String subjectCode,
        String actionName,
        Long profileId,
        String profileCode,
        String profileName,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        String uploadStatus,
        String sessionStatus,
        LocalDateTime createdAt,
        List<DataAssetResponse> assets
) {
}
