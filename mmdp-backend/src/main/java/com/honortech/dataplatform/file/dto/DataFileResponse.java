package com.honortech.dataplatform.file.dto;

import java.time.LocalDateTime;

public record DataFileResponse(
        Long id,
        Long taskId,
        String originalFilename,
        String fileExt,
        String contentType,
        Long fileSize,
        String bucketName,
        String objectKey,
        String storageUrl,
        String uploadStatus,
        LocalDateTime createdAt
) {
}
