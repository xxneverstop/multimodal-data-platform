package com.honortech.dataplatform.common.storage;

public record StoredFile(
        StorageProvider storageProvider,
        String bucketName,
        String objectKey,
        String fileName,
        long fileSize,
        String contentType,
        String storageUrl
) {
}
