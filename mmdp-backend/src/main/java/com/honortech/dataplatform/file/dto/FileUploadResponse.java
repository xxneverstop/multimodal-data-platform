package com.honortech.dataplatform.file.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record FileUploadResponse(
        DataFileResponse file,
        String qcStatus,
        String summary,
        JsonNode reportJson
) {
}
