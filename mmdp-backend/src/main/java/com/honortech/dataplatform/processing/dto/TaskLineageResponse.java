package com.honortech.dataplatform.processing.dto;

import java.util.List;

public record TaskLineageResponse(
        List<TaskLineageNodeResponse> nodes,
        List<TaskLineageEdgeResponse> edges
) {
}
