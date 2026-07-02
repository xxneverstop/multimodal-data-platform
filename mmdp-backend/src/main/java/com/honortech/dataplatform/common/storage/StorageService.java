package com.honortech.dataplatform.common.storage;

import java.io.InputStream;

public interface StorageService {

    StorageProvider provider();

    StoredFile upload(String objectKey, byte[] content, String contentType, String fileName);

    InputStream download(String bucketName, String objectKey);

    void copyObject(String sourceBucketName, String sourceObjectKey, String targetBucketName, String targetObjectKey);

    long getObjectSize(String bucketName, String objectKey);

    ObjectStat headObject(String bucketName, String objectKey);

    TemporaryCredentials assumeUploadCredentials(String bucketName, String objectKey, String roleSessionName);

    void deleteObject(String bucketName, String objectKey);
}
