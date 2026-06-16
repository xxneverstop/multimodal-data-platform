package com.honortech.dataplatform.common.auth;

import com.honortech.dataplatform.common.exception.ForbiddenException;
import com.honortech.dataplatform.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpSession;

public final class AuthSessionUtils {

    public static final String SESSION_USER_KEY = "MMDP_SESSION_USER";

    private AuthSessionUtils() {
    }

    public static AuthSessionUser getCurrentUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER_KEY);
        if (value instanceof AuthSessionUser authSessionUser) {
            return authSessionUser;
        }
        return null;
    }

    public static AuthSessionUser getRequiredUser(HttpSession session) {
        AuthSessionUser currentUser = getCurrentUser(session);
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }
        return currentUser;
    }

    public static void requireAdmin(HttpSession session) {
        AuthSessionUser currentUser = getRequiredUser(session);
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException("当前用户无权限访问该接口");
        }
    }
}
