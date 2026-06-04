package com.honortech.dataplatform.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private StorageProvider defaultProvider = StorageProvider.OSS;
    private final OssProperties oss = new OssProperties();

    public StorageProvider getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(StorageProvider defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public OssProperties getOss() {
        return oss;
    }

    public static class OssProperties {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
        private String region;
        private String stsRoleArn;
        private String stsRoleSessionName = "mmdp-direct-upload";
        private Integer stsDurationSeconds = 900;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getStsRoleArn() {
            return stsRoleArn;
        }

        public void setStsRoleArn(String stsRoleArn) {
            this.stsRoleArn = stsRoleArn;
        }

        public String getStsRoleSessionName() {
            return stsRoleSessionName;
        }

        public void setStsRoleSessionName(String stsRoleSessionName) {
            this.stsRoleSessionName = stsRoleSessionName;
        }

        public Integer getStsDurationSeconds() {
            return stsDurationSeconds;
        }

        public void setStsDurationSeconds(Integer stsDurationSeconds) {
            this.stsDurationSeconds = stsDurationSeconds;
        }
    }
}
