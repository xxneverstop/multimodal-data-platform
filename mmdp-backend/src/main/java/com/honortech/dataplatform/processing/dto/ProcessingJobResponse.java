package com.honortech.dataplatform.processing.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ProcessingJobResponse(
        Long id,
        Long taskId,
        String pipelineId,
        String status,
        JsonNode parameters,
        JsonNode resultJson,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
