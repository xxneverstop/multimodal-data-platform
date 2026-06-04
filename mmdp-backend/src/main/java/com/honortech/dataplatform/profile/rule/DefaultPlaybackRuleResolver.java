package com.honortech.dataplatform.profile.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DefaultPlaybackRuleResolver implements PlaybackRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        return "MULTI_VIDEO_IMU_V1".equalsIgnoreCase(ruleCode);
    }

    @Override
    public Map<String, SessionPlaybackResponse.PlaybackSource> buildSources(
            CollectionProfile profile,
            List<CollectionProfileSource> profileSources,
            List<DataFile> sessionFiles,
            JsonNode manifest) {
        Map<String, SessionPlaybackResponse.PlaybackSource> result = new LinkedHashMap<>();
        JsonNode sourceNode = manifest.get("sources");
        for (CollectionProfileSource profileSource : profileSources) {
            JsonNode sourceInfo = sourceNode == null ? null : sourceNode.get(profileSource.getSourceKey());
            String type = sourceInfo != null && sourceInfo.has("type") ? sourceInfo.get("type").asText() : profileSource.getSourceType();
            Double fps = sourceInfo != null && sourceInfo.has("fps") ? sourceInfo.get("fps").asDouble() : profileSource.getExpectedFps();
            Integer sampleCount = sourceInfo != null && sourceInfo.has("sampleCount") ? sourceInfo.get("sampleCount").asInt() : null;
            Double sampleRate = sourceInfo != null && sourceInfo.has("sampleRate") ? sourceInfo.get("sampleRate").asDouble() : profileSource.getExpectedSampleRate();
            String videoUrl = null;
            String jsonlUrl = null;
            for (DataFile dataFile : sessionFiles) {
                if (dataFile.getSourceKey() == null || !dataFile.getSourceKey().equalsIgnoreCase(profileSource.getSourceKey())) {
                    continue;
                }
                String lower = (dataFile.getOriginalFilename() == null ? "" : dataFile.getOriginalFilename()).toLowerCase(Locale.ROOT);
                if (lower.endsWith(".mp4")) {
                    videoUrl = "/api/files/" + dataFile.getId() + "/download";
                } else if (lower.endsWith(".jsonl")) {
                    jsonlUrl = "/api/files/" + dataFile.getId() + "/download";
                }
            }
            result.put(profileSource.getSourceKey(), new SessionPlaybackResponse.PlaybackSource(
                    type,
                    profileSource.getSourceName(),
                    videoUrl,
                    fps,
                    sampleCount,
                    jsonlUrl,
                    sampleRate
            ));
        }
        return result;
    }
}
