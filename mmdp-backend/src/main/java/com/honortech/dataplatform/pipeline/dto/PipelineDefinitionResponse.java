package com.honortech.dataplatform.pipeline.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineDefinitionResponse(
        Long id,
        String pipelineId,
        String displayName,
        String description,
        List<String> inputAssetTypes,
        List<String> outputAssetTypes,
        String executorType,
        Integer enabled,
        List<Long> profileIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
