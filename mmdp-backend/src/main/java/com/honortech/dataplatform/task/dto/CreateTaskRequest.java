package com.honortech.dataplatform.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank String taskName,
        @NotBlank String subjectCode,
        @NotBlank String actionName,
        @NotBlank String deviceType,
        @NotBlank String modality,
        @NotNull LocalDate collectDate,
        String scene,
        String operatorName,
        String captureLocation,
        String remark
) {
}
