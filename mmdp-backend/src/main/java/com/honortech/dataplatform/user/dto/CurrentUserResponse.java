package com.honortech.dataplatform.user.dto;

public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        String roleCode,
        boolean isAdmin
) {
}
