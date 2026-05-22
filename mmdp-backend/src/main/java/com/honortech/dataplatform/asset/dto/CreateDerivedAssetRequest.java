package com.honortech.dataplatform.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDerivedAssetRequest(
        @NotBlank String assetName,
        @NotBlank String assetType,
        @NotBlank String sourceType,
        Long fileId,
        String externalPath,
        String description
) {
}
