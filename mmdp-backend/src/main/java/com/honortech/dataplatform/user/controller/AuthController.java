package com.honortech.dataplatform.user.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.auth.AuthSessionUser;
import com.honortech.dataplatform.common.auth.AuthSessionUtils;
import com.honortech.dataplatform.common.exception.UnauthorizedException;
import com.honortech.dataplatform.user.dto.CurrentUserResponse;
import com.honortech.dataplatform.user.dto.LoginRequest;
import com.honortech.dataplatform.user.service.SysUserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserService sysUserService;

    public AuthController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        CurrentUserResponse currentUser = sysUserService.login(request.username(), request.password());
        session.setAttribute(
                AuthSessionUtils.SESSION_USER_KEY,
                new AuthSessionUser(
                        currentUser.id(),
                        currentUser.username(),
                        currentUser.displayName(),
                        currentUser.roleCode(),
                        currentUser.isAdmin()
                )
        );
        return ApiResponse.success("登录成功", currentUser);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success("退出成功", null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(HttpSession session) {
        AuthSessionUser currentUser = AuthSessionUtils.getCurrentUser(session);
        if (currentUser == null) {
            throw new UnauthorizedException("当前未登录");
        }
        return ApiResponse.success(new CurrentUserResponse(
                currentUser.userId(),
                currentUser.username(),
                currentUser.displayName(),
                currentUser.roleCode(),
                currentUser.isAdmin()
        ));
    }
}
