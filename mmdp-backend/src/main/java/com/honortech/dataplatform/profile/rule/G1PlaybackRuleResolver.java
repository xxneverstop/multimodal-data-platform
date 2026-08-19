package com.honortech.dataplatform.profile.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** G1 播放规则：优先返回完整双目产物，并兼容历史单目产物。 */
@Component
@Order(0)
public class G1PlaybackRuleResolver implements PlaybackRuleResolver {

    static final String RULE_CODE = "G1_TELEOP_V1";
    static final String PROFILE_CAMERA_SOURCE_KEY = "camera_svo2";
    static final String LEFT_SOURCE_KEY = "camera_svo2_left";
    static final String RIGHT_SOURCE_KEY = "camera_svo2_right";
    static final String LEGACY_FILENAME = "g1_playback.mp4";
    private static final double DEFAULT_FPS = 20D;

    @Override
    public boolean supports(String ruleCode) {
        return RULE_CODE.equalsIgnoreCase(ruleCode);
    }

    @Override
    public Map<String, SessionPlaybackResponse.PlaybackSource> buildSources(
            CollectionProfile profile,
            List<CollectionProfileSource> profileSources,
            List<DataFile> sessionFiles,
            JsonNode manifest) {
        DataFile leftFile = findMp4BySourceKey(sessionFiles, LEFT_SOURCE_KEY);
        DataFile rightFile = findMp4BySourceKey(sessionFiles, RIGHT_SOURCE_KEY);
        double fps = resolveFps(profileSources);

        Map<String, SessionPlaybackResponse.PlaybackSource> result = new LinkedHashMap<>();
        if (leftFile != null && rightFile != null) {
            result.put(LEFT_SOURCE_KEY, videoSource("ZED 左眼", leftFile, fps));
            result.put(RIGHT_SOURCE_KEY, videoSource("ZED 右眼", rightFile, fps));
            return result;
        }

        DataFile legacyFile = findLegacyMp4(sessionFiles);
        if (legacyFile != null) {
            result.put(LEFT_SOURCE_KEY, videoSource("ZED 左眼（旧版）", legacyFile, fps));
        }
        return result;
    }

    @Override
    public boolean canPlay(List<CollectionProfileSource> profileSources, List<DataFile> sessionFiles) {
        boolean hasStereoPair = findMp4BySourceKey(sessionFiles, LEFT_SOURCE_KEY) != null
                && findMp4BySourceKey(sessionFiles, RIGHT_SOURCE_KEY) != null;
        return hasStereoPair || findLegacyMp4(sessionFiles) != null;
    }

    private static SessionPlaybackResponse.PlaybackSource videoSource(
            String label, DataFile file, double fps) {
        return new SessionPlaybackResponse.PlaybackSource(
                "video",
                label,
                "/api/files/" + file.getId() + "/download",
                fps,
                null,
                null,
                null
        );
    }

    private static double resolveFps(List<CollectionProfileSource> profileSources) {
        return profileSources.stream()
                .filter(source -> source.getSourceKey() != null
                        && PROFILE_CAMERA_SOURCE_KEY.equalsIgnoreCase(source.getSourceKey()))
                .map(CollectionProfileSource::getExpectedFps)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(DEFAULT_FPS);
    }

    private static DataFile findMp4BySourceKey(List<DataFile> sessionFiles, String sourceKey) {
        DataFile fallback = null;
        for (DataFile file : sessionFiles) {
            if (file.getSourceKey() == null || !sourceKey.equalsIgnoreCase(file.getSourceKey())) {
                continue;
            }
            if (!isMp4(file)) {
                continue;
            }
            if ("PROCESSED_OUTPUT".equals(file.getFileRole())) {
                return file;
            }
            if (fallback == null) {
                fallback = file;
            }
        }
        return fallback;
    }

    private static DataFile findLegacyMp4(List<DataFile> sessionFiles) {
        DataFile fallback = null;
        for (DataFile file : sessionFiles) {
            if (file.getSourceKey() == null
                    || !PROFILE_CAMERA_SOURCE_KEY.equalsIgnoreCase(file.getSourceKey())) {
                continue;
            }
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (!LEGACY_FILENAME.equalsIgnoreCase(filename)) {
                continue;
            }
            if ("PROCESSED_OUTPUT".equals(file.getFileRole())) {
                return file;
            }
            if (fallback == null) {
                fallback = file;
            }
        }
        return fallback;
    }

    private static boolean isMp4(DataFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        return filename.toLowerCase(Locale.ROOT).endsWith(".mp4");
    }
}
