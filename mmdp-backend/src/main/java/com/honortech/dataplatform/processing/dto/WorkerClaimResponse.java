package com.honortech.dataplatform.processing.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record WorkerClaimResponse(
        Long jobId,
        Long taskId,
        Long sessionId,
        String pipelineId,
        JsonNode parameters,
        List<WorkerInputFile> inputFiles,
        LocalDateTime createdAt
) {
    public record WorkerInputFile(
            String sourceKey,
            String assetType,
            String originalFilename,
            String objectKey,
            String bucketName,
            String ossEndpoint,
            Long fileSize
    ) {}
}
