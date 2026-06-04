package com.honortech.dataplatform.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InitiateDirectUploadRequest(
        @NotBlank String fileName,
        @NotNull @Min(1) Long fileSize,
        @NotBlank String contentType,
        Long sessionId,
        String assetType
) {
}
