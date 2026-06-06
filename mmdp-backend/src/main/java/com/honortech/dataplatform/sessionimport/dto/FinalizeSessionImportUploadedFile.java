package com.honortech.dataplatform.sessionimport.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FinalizeSessionImportUploadedFile(
        @NotBlank String originalFilename,
        @NotBlank String relativePath,
        @NotBlank String objectKey,
        @NotBlank String contentType,
        @NotNull @Min(0) Long fileSize,
        String sha256
) {
}
