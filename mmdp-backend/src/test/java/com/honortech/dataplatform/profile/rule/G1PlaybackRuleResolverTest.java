package com.honortech.dataplatform.profile.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class G1PlaybackRuleResolverTest {

    private final G1PlaybackRuleResolver resolver = new G1PlaybackRuleResolver();

    @Test
    void shouldOnlySupportG1Rule() {
        assertTrue(resolver.supports("G1_TELEOP_V1"));
        assertTrue(resolver.supports("g1_teleop_v1"));
        assertFalse(resolver.supports("FAKE_STEREO_PLAYBACK_V2"));
    }

    @Test
    void shouldBuildOrderedStereoSources() {
        List<DataFile> files = List.of(
                video(102L, "camera_svo2_right", "g1_playback_right.mp4"),
                video(101L, "camera_svo2_left", "g1_playback_left.mp4")
        );

        assertTrue(resolver.canPlay(List.of(cameraSource(30D)), files));
        var sources = resolver.buildSources(
                new CollectionProfile(),
                List.of(cameraSource(30D)),
                files,
                new ObjectMapper().createObjectNode()
        );

        assertEquals(List.of("camera_svo2_left", "camera_svo2_right"),
                new ArrayList<>(sources.keySet()));
        assertEquals("ZED 左眼", sources.get("camera_svo2_left").label());
        assertEquals("/api/files/101/download", sources.get("camera_svo2_left").videoUrl());
        assertEquals("ZED 右眼", sources.get("camera_svo2_right").label());
        assertEquals("/api/files/102/download", sources.get("camera_svo2_right").videoUrl());
        assertEquals(30D, sources.get("camera_svo2_left").fps());
    }

    @Test
    void shouldRejectPartialStereoPair() {
        for (DataFile partialFile : List.of(
                video(101L, "camera_svo2_left", "g1_playback_left.mp4"),
                video(102L, "camera_svo2_right", "g1_playback_right.mp4"))) {
            List<DataFile> files = List.of(partialFile);
            assertFalse(resolver.canPlay(List.of(cameraSource(null)), files));
            assertTrue(resolver.buildSources(
                    new CollectionProfile(),
                    List.of(cameraSource(null)),
                    files,
                    new ObjectMapper().createObjectNode()
            ).isEmpty());
        }
    }

    @Test
    void shouldFallbackToLegacySingleEyeOutput() {
        List<DataFile> files = List.of(video(88L, "camera_svo2", "g1_playback.mp4"));

        assertTrue(resolver.canPlay(List.of(cameraSource(null)), files));
        var sources = resolver.buildSources(
                new CollectionProfile(),
                List.of(cameraSource(null)),
                files,
                new ObjectMapper().createObjectNode()
        );

        assertEquals(1, sources.size());
        assertEquals("ZED 左眼（旧版）", sources.get("camera_svo2_left").label());
        assertEquals("/api/files/88/download", sources.get("camera_svo2_left").videoUrl());
        assertEquals(20D, sources.get("camera_svo2_left").fps());
    }

    @Test
    void shouldPreferStereoPairWhenLegacyOutputAlsoExists() {
        List<DataFile> files = List.of(
                video(88L, "camera_svo2", "g1_playback.mp4"),
                video(101L, "camera_svo2_left", "g1_playback_left.mp4"),
                video(102L, "camera_svo2_right", "g1_playback_right.mp4")
        );

        var sources = resolver.buildSources(
                new CollectionProfile(),
                List.of(cameraSource(20D)),
                files,
                new ObjectMapper().createObjectNode()
        );

        assertEquals(2, sources.size());
        assertEquals("/api/files/101/download", sources.get("camera_svo2_left").videoUrl());
        assertEquals("/api/files/102/download", sources.get("camera_svo2_right").videoUrl());
    }

    private static CollectionProfileSource cameraSource(Double fps) {
        CollectionProfileSource source = new CollectionProfileSource();
        source.setSourceKey("camera_svo2");
        source.setExpectedFps(fps);
        return source;
    }

    private static DataFile video(long id, String sourceKey, String filename) {
        DataFile file = new DataFile();
        file.setId(id);
        file.setSourceKey(sourceKey);
        file.setOriginalFilename(filename);
        file.setFileRole("PROCESSED_OUTPUT");
        return file;
    }
}
