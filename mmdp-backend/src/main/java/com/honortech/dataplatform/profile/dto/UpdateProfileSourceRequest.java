package com.honortech.dataplatform.profile.dto;

public record UpdateProfileSourceRequest(
        String sourceKey,

        String sourceName,

        String sourceType,

        String deviceRoleCode,

        Boolean requiredFlag,

        String filePattern,

        String parsedAssetType,

        String playbackKind,

        Double expectedFps,

        Double expectedSampleRate,

        Integer sortOrder
) {}
