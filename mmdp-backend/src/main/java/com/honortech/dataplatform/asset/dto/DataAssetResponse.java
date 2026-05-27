package com.honortech.dataplatform.asset.dto;

import java.time.LocalDateTime;

public record DataAssetResponse(
        Long id,
        Long taskId,
        String sourceType,
        String assetType,
        String displayName,
        Long fileId,
        String originalFilename,
        String fileExt,
        String contentType,
        Long fileSize,
        String uploadStatus,
        String externalPath,
        String fileFormat,
        String sizeRemark,
        String description,
        String operatorRemark,
        Long producedByJobId,
        LocalDateTime createdAt,
        String objectKey,
        String storageUrl
) {
}
