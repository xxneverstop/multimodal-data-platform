package com.honortech.dataplatform.file.dto;

import java.time.LocalDateTime;

public record DataFileResponse(
        Long id,
        Long taskId,
        Long sessionId,
        String fileRole,
        String sourceKey,
        String originalFilename,
        String relativePath,
        String fileExt,
        String contentType,
        Long fileSize,
        String sha256,
        String assetType,
        String storageProvider,
        String bucketName,
        String objectKey,
        String storageUrl,
        String uploadStatus,
        LocalDateTime createdAt
) {
}
