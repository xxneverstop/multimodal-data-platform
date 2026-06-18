package com.honortech.dataplatform.profile.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;

import java.util.List;
import java.util.Map;

public interface PlaybackRuleResolver {

    boolean supports(String ruleCode);

    Map<String, SessionPlaybackResponse.PlaybackSource> buildSources(
            CollectionProfile profile,
            List<CollectionProfileSource> profileSources,
            List<DataFile> sessionFiles,
            JsonNode manifest
    );

    /** 检查给定文件集合是否满足此播放规则的最低要求 */
    boolean canPlay(List<CollectionProfileSource> profileSources, List<DataFile> sessionFiles);
}
