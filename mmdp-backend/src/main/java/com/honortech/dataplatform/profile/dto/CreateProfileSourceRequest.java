package com.honortech.dataplatform.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProfileSourceRequest(
        @NotBlank(message = "sourceKey不能为空")
        String sourceKey,

        @NotBlank(message = "sourceName不能为空")
        String sourceName,

        @NotBlank(message = "sourceType不能为空")
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
