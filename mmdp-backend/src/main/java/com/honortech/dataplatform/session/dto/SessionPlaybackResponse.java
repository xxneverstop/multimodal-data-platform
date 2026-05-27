package com.honortech.dataplatform.session.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record SessionPlaybackResponse(
        String sessionId,
        Long taskId,
        String subjectCode,
        String actionName,
        LocalDateTime startedAt,
        Long durationMs,
        Map<String, PlaybackSource> sources
) {

    public record PlaybackSource(
            String type,
            String label,
            String videoUrl,
            Double fps,
            Integer sampleCount,
            String jsonlUrl,
            Double sampleRate
    ) {
    }
}
