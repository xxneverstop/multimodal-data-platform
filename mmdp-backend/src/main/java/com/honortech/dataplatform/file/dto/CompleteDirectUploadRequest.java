package com.honortech.dataplatform.file.dto;

import jakarta.validation.constraints.NotNull;

public record CompleteDirectUploadRequest(
        @NotNull Long fileId
) {
}
