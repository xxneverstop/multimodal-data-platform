package com.honortech.dataplatform.sessionimport.dto;

public record FinalizeSessionImportResponse(
        Long importId,
        Long platformTaskId,
        Long platformSessionId,
        String platformSessionCode,
        String localSessionId,
        String profileCode,
        String subjectCode,
        String status,
        boolean existing,
        int createdFileCount,
        int createdAssetCount,
        int sourceCount
) {
}
