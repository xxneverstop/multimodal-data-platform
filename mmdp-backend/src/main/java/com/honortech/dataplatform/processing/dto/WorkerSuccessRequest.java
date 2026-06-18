package com.honortech.dataplatform.processing.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WorkerSuccessRequest(
        @NotEmpty List<OutputFile> outputFiles,
        String message
) {
    public record OutputFile(
            String sourceKey,
            String fileName,
            String objectKey,
            String assetType,
            String contentType,
            Long fileSize
    ) {}
}
