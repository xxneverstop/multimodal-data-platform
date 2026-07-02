package com.honortech.dataplatform.profile.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DefaultPlaybackRuleResolver implements PlaybackRuleResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultPlaybackRuleResolver.class);

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
            Double fps = null;
            if (sourceInfo != null && sourceInfo.has("fps")) { fps = sourceInfo.get("fps").asDouble(); }
            if (fps == null) { fps = profileSource.getExpectedFps(); }
            Integer sampleCount = sourceInfo != null && sourceInfo.has("sampleCount") ? sourceInfo.get("sampleCount").asInt() : null;
            Double sampleRate = null;
            if (sourceInfo != null && sourceInfo.has("sampleRate")) { sampleRate = sourceInfo.get("sampleRate").asDouble(); }
            if (sampleRate == null) { sampleRate = profileSource.getExpectedSampleRate(); }
            String videoUrl = null;
            String jsonlUrl = null;
            // 优先取处理产物（PROCESSED_OUTPUT），其次取原始文件
            for (DataFile dataFile : sessionFiles) {
                if (dataFile.getSourceKey() == null || !dataFile.getSourceKey().equalsIgnoreCase(profileSource.getSourceKey())) {
                    continue;
                }
                String lower = (dataFile.getOriginalFilename() == null ? "" : dataFile.getOriginalFilename()).toLowerCase(Locale.ROOT);
                boolean isProcessed = "PROCESSED_OUTPUT".equals(dataFile.getFileRole());
                if (lower.endsWith(".mp4")) {
                    // 处理产物优先：已有非产物 URL 时用产物覆盖，已有产物 URL 不覆盖
                    if (videoUrl == null || isProcessed) {
                        videoUrl = "/api/files/" + dataFile.getId() + "/download";
                    }
                } else if (lower.endsWith(".jsonl") || lower.endsWith(".csv")) {
                    if (jsonlUrl == null || isProcessed) {
                        jsonlUrl = "/api/files/" + dataFile.getId() + "/download";
                    }
                }
            }
            log.info("Playback source [{}]: videoUrl={}, jsonlUrl={}, playbackKind={}",
                    profileSource.getSourceKey(), videoUrl, jsonlUrl, profileSource.getPlaybackKind());
            // 根据实际可用的媒体文件修正播放类型
            String resolvedType = type;
            if (videoUrl != null) {
                resolvedType = "video";
            } else if (jsonlUrl != null) {
                resolvedType = "imu";
            } else if (profileSource.getPlaybackKind() != null && !profileSource.getPlaybackKind().isBlank()) {
                // 无产物文件，但 Profile 标记了该 source 的播放方式（如 IMU 曲线）
                resolvedType = profileSource.getPlaybackKind();
            } else {
                // 无法播放的 source，跳过
                continue;
            }
            result.put(profileSource.getSourceKey(), new SessionPlaybackResponse.PlaybackSource(
                    resolvedType,
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

    @Override
    public boolean canPlay(List<CollectionProfileSource> profileSources, List<DataFile> sessionFiles) {
        for (CollectionProfileSource ps : profileSources) {
            String pk = ps.getPlaybackKind();
            if (pk == null || pk.isBlank()) continue;
            for (DataFile f : sessionFiles) {
                if (f.getSourceKey() == null) continue;
                if (!f.getSourceKey().equalsIgnoreCase(ps.getSourceKey())) continue;
                String name = (f.getOriginalFilename() == null) ? "" : f.getOriginalFilename().toLowerCase(Locale.ROOT);
                if ("video".equals(pk) && name.endsWith(".mp4")) return true;
                if (("imu".equals(pk) || "imu_curve".equals(pk)) && (name.endsWith(".csv") || name.endsWith(".jsonl"))) return true;
            }
        }
        return false;
    }
}
