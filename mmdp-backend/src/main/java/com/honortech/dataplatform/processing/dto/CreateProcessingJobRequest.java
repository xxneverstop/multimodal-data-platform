package com.honortech.dataplatform.processing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record CreateProcessingJobRequest(
        @NotBlank String pipelineId,
        JsonNode parameters
) {
}
