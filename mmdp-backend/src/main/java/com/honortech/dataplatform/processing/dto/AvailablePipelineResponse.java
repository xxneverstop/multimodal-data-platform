package com.honortech.dataplatform.processing.dto;

import java.util.List;

public record AvailablePipelineResponse(
        String pipelineId,
        String displayName,
        String description,
        String readinessStatus,
        List<String> missingRequiredAssets,
        List<String> existingAssets,
        List<String> suggestedNextActions
) {
}
