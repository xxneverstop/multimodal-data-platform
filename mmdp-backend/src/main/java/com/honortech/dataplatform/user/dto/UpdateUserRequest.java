package com.honortech.dataplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 128, message = "长度不能超过128")
        String displayName,
        @NotBlank(message = "不能为空")
        String roleCode,
        Boolean isAdmin,
        @NotBlank(message = "不能为空")
        String status,
        @Size(max = 32, message = "长度不能超过32")
        String phone,
        @Size(max = 128, message = "长度不能超过128")
        String email,
        @Size(max = 512, message = "长度不能超过512")
        String remark
) {
}
