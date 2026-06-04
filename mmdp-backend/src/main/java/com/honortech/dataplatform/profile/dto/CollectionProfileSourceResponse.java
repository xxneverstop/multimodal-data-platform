package com.honortech.dataplatform.profile.dto;

public record CollectionProfileSourceResponse(
        Long id,
        String sourceKey,
        String sourceName,
        String sourceType,
        String deviceRoleCode,
        Boolean required,
        String filePattern,
        String parsedAssetType,
        String playbackKind,
        Double expectedFps,
        Double expectedSampleRate,
        Integer sortOrder
) {
}
