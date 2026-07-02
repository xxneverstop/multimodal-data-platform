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

/**
 * Fake Stereo+IMU 播放规则。
 * 从 Session 的 DataFile 中匹配合适的视频 + IMU 源，构建 PlaybackView 所需的 PlaybackSource。
 * 要求同时满足 video + imu 两类 source 才判定可播放。
 */
@Component
public class FakeStereoPlaybackRuleResolver implements PlaybackRuleResolver {

    private static final Logger log = LoggerFactory.getLogger(FakeStereoPlaybackRuleResolver.class);

    @Override
    public boolean supports(String ruleCode) {
        return "FAKE_STEREO_PLAYBACK_V2".equalsIgnoreCase(ruleCode);
    }

    @Override
    public Map<String, SessionPlaybackResponse.PlaybackSource> buildSources(
            CollectionProfile profile,
            List<CollectionProfileSource> profileSources,
            List<DataFile> sessionFiles,
            JsonNode manifest) {

        Map<String, SessionPlaybackResponse.PlaybackSource> result = new LinkedHashMap<>();

        for (CollectionProfileSource profileSource : profileSources) {
            String playbackKind = profileSource.getPlaybackKind();
            if (playbackKind == null || playbackKind.isBlank()) {
                continue;
            }

            String videoUrl = null;
            String jsonlUrl = null;

            for (DataFile dataFile : sessionFiles) {
                if (dataFile.getSourceKey() == null
                        || !dataFile.getSourceKey().equalsIgnoreCase(profileSource.getSourceKey())) {
                    continue;
                }

                String lower = (dataFile.getOriginalFilename() == null ? ""
                        : dataFile.getOriginalFilename()).toLowerCase(Locale.ROOT);
                boolean isProcessed = "PROCESSED_OUTPUT".equals(dataFile.getFileRole());

                if ("video".equals(playbackKind) && lower.endsWith(".mp4")) {
                    if (videoUrl == null || isProcessed) {
                        videoUrl = "/api/files/" + dataFile.getId() + "/download";
                    }
                }

                if (("imu".equals(playbackKind) || "imu_curve".equals(playbackKind))
                        && (lower.endsWith(".csv") || lower.endsWith(".jsonl"))) {
                    if (jsonlUrl == null || isProcessed) {
                        jsonlUrl = "/api/files/" + dataFile.getId() + "/download";
                    }
                }
            }

            if (videoUrl == null && jsonlUrl == null) {
                log.info("Playback source [{}]: no matching files, skip", profileSource.getSourceKey());
                continue;
            }

            String resolvedType = videoUrl != null ? "video" : playbackKind;

            result.put(profileSource.getSourceKey(),
                    new SessionPlaybackResponse.PlaybackSource(
                            resolvedType,
                            profileSource.getSourceName(),
                            videoUrl,
                            profileSource.getExpectedFps(),
                            null,
                            jsonlUrl,
                            profileSource.getExpectedSampleRate()
                    ));
        }

        return result;
    }

    @Override
    public boolean canPlay(List<CollectionProfileSource> profileSources,
                           List<DataFile> sessionFiles) {
        boolean hasVideo = false;
        boolean hasImu = false;

        for (CollectionProfileSource ps : profileSources) {
            String pk = ps.getPlaybackKind();
            if (pk == null || pk.isBlank()) continue;

            for (DataFile f : sessionFiles) {
                if (f.getSourceKey() == null) continue;
                if (!f.getSourceKey().equalsIgnoreCase(ps.getSourceKey())) continue;

                String name = (f.getOriginalFilename() == null)
                        ? "" : f.getOriginalFilename().toLowerCase(Locale.ROOT);

                if ("video".equals(pk) && name.endsWith(".mp4")) {
                    hasVideo = true;
                }
                if (("imu".equals(pk) || "imu_curve".equals(pk))
                        && (name.endsWith(".csv") || name.endsWith(".jsonl"))) {
                    hasImu = true;
                }
            }
        }

        return hasVideo && hasImu;
    }
}
