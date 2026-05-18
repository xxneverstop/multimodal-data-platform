package com.honortech.dataplatform.qc.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record QcReportResponse(
        Long id,
        Long taskId,
        Long fileId,
        String qcStatus,
        String summary,
        JsonNode reportJson,
        LocalDateTime createdAt
) {
}
