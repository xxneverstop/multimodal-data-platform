package com.honortech.dataplatform.file.dto;

public record InitiateDirectUploadResponse(
        Long fileId,
        Long sessionId,
        String sessionCode,
        String bucketName,
        String region,
        String endpoint,
        String objectKey,
        String stsAccessKeyId,
        String stsAccessKeySecret,
        String stsSecurityToken,
        String expiration
) {
}
