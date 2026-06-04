package com.honortech.dataplatform.sessionimport.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;

public record NormalizedSessionManifest(
        String schemaVersion,
        String clientId,
        String localTaskId,
        String localSessionId,
        Long platformTaskId,
        String platformTaskCode,
        String taskName,
        String profileCode,
        String profileName,
        String subjectName,
        String subjectCode,
        String actionName,
        String actionCode,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        String timestampPolicy,
        JsonNode sources,
        JsonNode artifacts,
        JsonNode rawManifest,
        Map<String, String> sourceTypeByName
) {
}
