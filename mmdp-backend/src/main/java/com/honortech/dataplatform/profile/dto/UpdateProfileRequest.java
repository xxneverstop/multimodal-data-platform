package com.honortech.dataplatform.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "profileCode不能为空")
        @Size(max = 64, message = "profileCode长度不能超过64")
        String profileCode,

        @NotBlank(message = "profileName不能为空")
        @Size(max = 128, message = "profileName长度不能超过128")
        String profileName,

        @NotBlank(message = "taskTypeCode不能为空")
        String taskTypeCode,

        String modalityGroupCode,

        String deviceGroupCode,

        String packageRuleCode,

        String parserRuleCode,

        String archiveRuleCode,

        String playbackRuleCode,

        @Size(max = 32, message = "version长度不能超过32")
        String version,

        @Size(max = 512, message = "remark长度不能超过512")
        String remark
) {}
