package com.honortech.dataplatform.user.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String roleCode,
        boolean isAdmin,
        String status,
        String phone,
        String email,
        String remark,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
