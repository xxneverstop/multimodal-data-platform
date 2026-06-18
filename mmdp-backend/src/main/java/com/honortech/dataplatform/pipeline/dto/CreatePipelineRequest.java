package com.honortech.dataplatform.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePipelineRequest(
        @NotBlank(message = "pipelineId不能为空")
        @Size(max = 64)
        String pipelineId,

        @NotBlank(message = "displayName不能为空")
        @Size(max = 128)
        String displayName,

        @Size(max = 512)
        String description,

        List<String> inputAssetTypes,

        List<String> outputAssetTypes,

        @NotBlank(message = "executorType不能为空")
        String executorType,

        List<Long> profileIds
) {}
