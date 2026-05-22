package com.honortech.dataplatform.processing.dto;

public record TaskLineageEdgeResponse(
        String source,
        String target,
        String label
) {
}
