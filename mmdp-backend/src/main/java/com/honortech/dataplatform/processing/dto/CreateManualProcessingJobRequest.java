package com.honortech.dataplatform.processing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateManualProcessingJobRequest(
        @NotBlank String pipelineId,
        @NotEmpty List<Long> inputAssetIds,
        @NotEmpty List<@Valid CreateDerivedAssetRequest> outputAssets,
        String operatorName,
        String toolName,
        String toolVersion,
        JsonNode paramsJson,
        String logPath,
        String remark
) {
}
