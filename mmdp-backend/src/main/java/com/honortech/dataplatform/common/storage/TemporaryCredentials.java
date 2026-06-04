package com.honortech.dataplatform.common.storage;

public record TemporaryCredentials(
        String accessKeyId,
        String accessKeySecret,
        String securityToken,
        String expiration
) {
}
