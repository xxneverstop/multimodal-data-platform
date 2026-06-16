package com.honortech.dataplatform.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserStatusRequest(
        @NotBlank(message = "不能为空")
        String status
) {
}
