package com.honortech.dataplatform.common.auth;

import java.io.Serializable;

public record AuthSessionUser(
        Long userId,
        String username,
        String displayName,
        String roleCode,
        boolean isAdmin
) implements Serializable {
}
