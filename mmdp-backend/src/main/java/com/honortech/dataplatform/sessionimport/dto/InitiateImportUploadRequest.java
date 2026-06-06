package com.honortech.dataplatform.sessionimport.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InitiateImportUploadRequest(
        @NotBlank String fileName,
        @NotBlank String relativePath,
        @NotBlank String importKey,
        @NotNull @Min(1) Long fileSize,
        @NotBlank String contentType
) {
}
