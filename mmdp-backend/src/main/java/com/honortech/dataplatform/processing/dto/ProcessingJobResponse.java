package com.honortech.dataplatform.processing.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ProcessingJobResponse(
        Long id,
        Long taskId,
        String pipelineId,
        String executorType,
        String status,
        JsonNode parameters,
        JsonNode paramsJson,
        JsonNode resultJson,
        String errorMessage,
        String operatorName,
        String toolName,
        String toolVersion,
        String logPath,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
