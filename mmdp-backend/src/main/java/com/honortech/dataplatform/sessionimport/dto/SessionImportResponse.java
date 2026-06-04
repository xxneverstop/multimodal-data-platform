package com.honortech.dataplatform.sessionimport.dto;

public record SessionImportResponse(
        Long importId,
        Long platformTaskId,
        Long platformSessionId,
        String status,
        boolean existing
) {
}
