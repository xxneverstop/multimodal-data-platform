package com.honortech.dataplatform.common.storage;

import com.honortech.dataplatform.common.exception.BizException;

public enum StorageProvider {
    OSS;

    public static StorageProvider fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("Storage provider must not be blank");
        }
        if (!OSS.name().equalsIgnoreCase(value.trim())) {
            throw new BizException("Unsupported storage provider: " + value);
        }
        return OSS;
    }
}
