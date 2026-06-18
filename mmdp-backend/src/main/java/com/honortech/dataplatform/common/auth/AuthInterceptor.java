package com.honortech.dataplatform.common.auth;

import com.honortech.dataplatform.common.exception.ForbiddenException;
import com.honortech.dataplatform.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/")) {
            return true;
        }
        if (requestUri.startsWith("/api/auth/")) {
            return true;
        }
        if (requestUri.startsWith("/api/worker/")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        AuthSessionUser currentUser = session == null ? null : AuthSessionUtils.getCurrentUser(session);
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }
        if (requestUri.startsWith("/api/admin/") && !currentUser.isAdmin()) {
            throw new ForbiddenException("当前用户无权限访问该接口");
        }
        return true;
    }
}
