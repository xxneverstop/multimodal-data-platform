package com.honortech.dataplatform.processing.dto;

import com.honortech.dataplatform.asset.dto.DataAssetResponse;

import java.util.List;

public record ManualProcessingJobResponse(
        ProcessingJobResponse job,
        List<DataAssetResponse> outputAssets
) {
}
