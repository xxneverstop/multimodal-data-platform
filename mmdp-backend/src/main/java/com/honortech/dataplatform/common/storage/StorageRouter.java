package com.honortech.dataplatform.common.storage;

import com.honortech.dataplatform.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageRouter {

    private final StorageService ossStorageService;

    public StorageRouter(List<StorageService> services, StorageProperties properties) {
        StorageProvider defaultProvider = properties.getDefaultProvider();
        if (defaultProvider != StorageProvider.OSS) {
            throw new BizException("Only OSS is supported as storage provider");
        }
        StorageService matched = null;
        for (StorageService service : services) {
            if (service.provider() == StorageProvider.OSS) {
                matched = service;
                break;
            }
        }
        if (matched == null) {
            throw new BizException("OSS storage service is not configured");
        }
        this.ossStorageService = matched;
    }

    public StorageService defaultService() {
        return ossStorageService;
    }

    public StorageService get(StorageProvider provider) {
        if (provider != StorageProvider.OSS) {
            throw new BizException("Unsupported storage provider: " + provider);
        }
        return ossStorageService;
    }
}
