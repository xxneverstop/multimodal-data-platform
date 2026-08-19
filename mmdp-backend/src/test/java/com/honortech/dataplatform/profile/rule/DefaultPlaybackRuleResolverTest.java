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
    void shouldBuildGenericPlaybackSourceFromGeneratedMp4() {
        DefaultPlaybackRuleResolver resolver = new DefaultPlaybackRuleResolver();
        CollectionProfileSource source = new CollectionProfileSource();
        source.setSourceKey("camera_main");
        source.setSourceName("主相机回放");
        source.setPlaybackKind("video");
        source.setExpectedFps(20D);

        DataFile file = new DataFile();
        file.setId(88L);
        file.setSourceKey("camera_main");
        file.setOriginalFilename("playback.mp4");
        file.setFileRole("PROCESSED_OUTPUT");

        assertTrue(resolver.supports("GENERIC_PROFILE_V1"));
        assertTrue(resolver.canPlay(List.of(source), List.of(file)));

        var sources = resolver.buildSources(
                new CollectionProfile(),
                List.of(source),
                List.of(file),
                new ObjectMapper().createObjectNode()
        );
        assertEquals("video", sources.get("camera_main").type());
        assertEquals("/api/files/88/download", sources.get("camera_main").videoUrl());
        assertEquals(20D, sources.get("camera_main").fps());
    }
}
