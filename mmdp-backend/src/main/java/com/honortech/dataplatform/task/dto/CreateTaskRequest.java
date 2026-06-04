package com.honortech.dataplatform.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank String taskName,
        String subjectCode,
        String subjectName,
        @NotBlank String actionName,
        Long profileId,
        String deviceType,
        String modality,
        @NotNull LocalDate collectDate,
        String scene,
        String operatorName,
        String captureLocation,
        String remark
) {
}
