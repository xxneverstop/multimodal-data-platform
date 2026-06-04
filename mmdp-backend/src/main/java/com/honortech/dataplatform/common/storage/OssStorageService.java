package com.honortech.dataplatform.common.storage;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.honortech.dataplatform.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Component
public class OssStorageService implements StorageService {

    private final StorageProperties properties;
    private volatile OSS ossClient;

    public OssStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.OSS;
    }

    @Override
    public StoredFile upload(String objectKey, byte[] content, String contentType, String fileName) {
        try {
            OSS ossClient = getClient();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(contentType);
            ossClient.putObject(
                    properties.getOss().getBucket(),
                    objectKey,
                    new ByteArrayInputStream(content),
                    metadata
            );
            return new StoredFile(
                    provider(),
                    properties.getOss().getBucket(),
                    objectKey,
                    fileName,
                    content.length,
                    contentType,
                    buildStorageUrl(objectKey)
            );
        } catch (Exception exception) {
            throw new BizException("Failed to upload file to OSS", exception);
        }
    }

    @Override
    public InputStream download(String bucketName, String objectKey) {
        try {
            OSS ossClient = getClient();
            OSSObject object = ossClient.getObject(bucketName, objectKey);
            return object.getObjectContent();
        } catch (Exception exception) {
            throw new BizException("Failed to download file from OSS", exception);
        }
    }

    @Override
    public long getObjectSize(String bucketName, String objectKey) {
        try {
            OSS ossClient = getClient();
            return ossClient.getSimplifiedObjectMeta(bucketName, objectKey).getSize();
        } catch (Exception exception) {
            throw new BizException("Failed to stat file in OSS", exception);
        }
    }

    @Override
    public ObjectStat headObject(String bucketName, String objectKey) {
        try {
            ObjectMetadata metadata = getClient().getObjectMetadata(bucketName, objectKey);
            return new ObjectStat(metadata.getContentLength(), metadata.getETag());
        } catch (Exception exception) {
            throw new BizException("Failed to head file in OSS", exception);
        }
    }

    @Override
    public TemporaryCredentials assumeUploadCredentials(String bucketName, String objectKey, String roleSessionName) {
        StorageProperties.OssProperties oss = properties.getOss();
        if (isBlank(oss.getStsRoleArn())) {
            throw new BizException("OSS STS role is not configured. Please set MMDP_OSS_STS_ROLE_ARN.");
        }
        try {
            String region = isBlank(oss.getRegion()) ? "cn-hangzhou" : oss.getRegion().trim();
            String endpoint = "sts." + region + ".aliyuncs.com";
            DefaultProfile.addEndpoint(region, region, "Sts", endpoint);
            IClientProfile profile = DefaultProfile.getProfile(region, oss.getAccessKeyId(), oss.getAccessKeySecret());
            DefaultAcsClient client = new DefaultAcsClient(profile);

            AssumeRoleRequest request = new AssumeRoleRequest();
            request.setSysMethod(MethodType.POST);
            request.setRoleArn(oss.getStsRoleArn());
            request.setRoleSessionName(roleSessionName);
            request.setDurationSeconds(Long.valueOf(oss.getStsDurationSeconds()));
            request.setPolicy(buildPolicy(bucketName, objectKey));

            AssumeRoleResponse response = client.getAcsResponse(request);
            AssumeRoleResponse.Credentials credentials = response.getCredentials();
            return new TemporaryCredentials(
                    credentials.getAccessKeyId(),
                    credentials.getAccessKeySecret(),
                    credentials.getSecurityToken(),
                    credentials.getExpiration()
            );
        } catch (Exception exception) {
            throw new BizException("Failed to assume OSS upload role", exception);
        }
    }

    private OSS getClient() {
        if (ossClient != null) {
            return ossClient;
        }
        synchronized (this) {
            if (ossClient == null) {
                ossClient = buildClient();
            }
            return ossClient;
        }
    }

    private OSS buildClient() {
        StorageProperties.OssProperties oss = properties.getOss();
        if (isBlank(oss.getAccessKeyId()) || isBlank(oss.getAccessKeySecret())) {
            throw new BizException("OSS credentials are not configured. Please set MMDP_OSS_ACCESS_KEY_ID and MMDP_OSS_ACCESS_KEY_SECRET.");
        }
        if (isBlank(oss.getEndpoint()) || isBlank(oss.getBucket())) {
            throw new BizException("OSS endpoint or bucket is not configured. Please set MMDP_OSS_ENDPOINT and MMDP_OSS_BUCKET.");
        }
        return new OSSClientBuilder().build(
                oss.getEndpoint(),
                oss.getAccessKeyId(),
                oss.getAccessKeySecret()
        );
    }

    private String buildStorageUrl(String objectKey) {
        return "oss://" + properties.getOss().getBucket() + "/" + objectKey;
    }

    private String buildPolicy(String bucketName, String objectKey) {
        String resource = "acs:oss:*:*:" + bucketName + "/" + objectKey;
        return """
                {
                  "Version": "1",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Action": [
                        "oss:PutObject",
                        "oss:InitiateMultipartUpload",
                        "oss:UploadPart",
                        "oss:CompleteMultipartUpload",
                        "oss:AbortMultipartUpload",
                        "oss:ListParts"
                      ],
                      "Resource": [
                        "%s"
                      ]
                    }
                  ]
                }
                """.formatted(escapeJson(resource));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
