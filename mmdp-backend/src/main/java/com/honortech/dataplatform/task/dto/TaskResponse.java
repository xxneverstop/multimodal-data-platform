package com.honortech.dataplatform.task.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String taskName,
        String subjectCode,
        String actionName,
        String deviceType,
        String modality,
        LocalDate collectDate,
        String scene,
        String operatorName,
        String captureLocation,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
