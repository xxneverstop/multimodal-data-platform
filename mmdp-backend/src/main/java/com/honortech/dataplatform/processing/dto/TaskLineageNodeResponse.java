package com.honortech.dataplatform.processing.dto;

public record TaskLineageNodeResponse(
        String id,
        String type,
        String label,
        String assetType,
        String pipelineId,
        String status,
        Long detailId
) {
}
