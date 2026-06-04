package com.honortech.dataplatform.profile.dto;

import java.util.List;

public record CollectionProfileResponse(
        Long id,
        String profileCode,
        String profileName,
        String taskTypeCode,
        String modalityGroupCode,
        String deviceGroupCode,
        String packageRuleCode,
        String parserRuleCode,
        String archiveRuleCode,
        String playbackRuleCode,
        String version,
        List<CollectionProfileSourceResponse> sources
) {
}
