package com.honortech.dataplatform.task.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String taskCode,
        String taskName,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String actionName,
        Long profileId,
        String profileName,
        String deviceType,
        String modality,
        LocalDate collectDate,
        String scene,
        String operatorName,
        String captureLocation,
        String status,
        String remark,
        Long sessionCount,
        LocalDateTime latestSessionStartedAt,
        String latestSessionStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
