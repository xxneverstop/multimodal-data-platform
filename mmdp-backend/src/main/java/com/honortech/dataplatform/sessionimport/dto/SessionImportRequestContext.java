package com.honortech.dataplatform.sessionimport.dto;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record SessionImportRequestContext(
        String sourceEndpoint,
        Long fallbackPlatformTaskId,
        MultipartFile manifestFile,
        MultipartFile archiveFile,
        List<MultipartFile> files
) {

    public SessionImportRequestContext {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
