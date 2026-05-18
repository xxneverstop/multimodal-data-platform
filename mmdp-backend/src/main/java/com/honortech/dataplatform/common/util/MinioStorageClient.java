package com.honortech.dataplatform.common.util;

import com.honortech.dataplatform.common.config.MinioProperties;
import com.honortech.dataplatform.common.exception.BizException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class MinioStorageClient {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioStorageClient(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public String upload(String objectKey, byte[] content, String contentType) {
        try {
            ensureBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(contentType)
                            .build()
            );
            return minioProperties.getBucket() + "/" + objectKey;
        } catch (Exception exception) {
            throw new BizException("Failed to upload file to MinIO", exception);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
        );
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
        }
    }
}
