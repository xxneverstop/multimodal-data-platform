package com.honortech.dataplatform.profile.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPlaybackRuleResolverTest {

    @Test
    void shouldBuildG1PlaybackSourceFromGeneratedMp4() {
        DefaultPlaybackRuleResolver resolver = new DefaultPlaybackRuleResolver();
        CollectionProfileSource source = new CollectionProfileSource();
        source.setSourceKey("camera_svo2");
        source.setSourceName("G1 相机回放");
        source.setPlaybackKind("video");
        source.setExpectedFps(20D);

        DataFile file = new DataFile();
        file.setId(88L);
        file.setSourceKey("camera_svo2");
        file.setOriginalFilename("g1_playback.mp4");
        file.setFileRole("PROCESSED_OUTPUT");

        assertTrue(resolver.supports("G1_TELEOP_V1"));
        assertTrue(resolver.canPlay(List.of(source), List.of(file)));

        var sources = resolver.buildSources(
                new CollectionProfile(),
                List.of(source),
                List.of(file),
                new ObjectMapper().createObjectNode()
        );
        assertEquals("video", sources.get("camera_svo2").type());
        assertEquals("/api/files/88/download", sources.get("camera_svo2").videoUrl());
        assertEquals(20D, sources.get("camera_svo2").fps());
    }
}
