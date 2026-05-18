package com.honortech.dataplatform.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateExternalAssetRequest(
        @NotBlank String assetType,
        @NotBlank String displayName,
        @NotBlank String externalPath,
        String fileFormat,
        String sizeRemark,
        String description,
        String operatorRemark
) {
}
