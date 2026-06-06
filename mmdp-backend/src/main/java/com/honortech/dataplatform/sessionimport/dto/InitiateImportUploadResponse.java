package com.honortech.dataplatform.sessionimport.dto;

public record InitiateImportUploadResponse(
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
