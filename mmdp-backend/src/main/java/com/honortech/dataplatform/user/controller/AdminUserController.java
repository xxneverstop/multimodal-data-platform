package com.honortech.dataplatform.user.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.user.dto.CreateUserRequest;
import com.honortech.dataplatform.user.dto.UpdateUserRequest;
import com.honortech.dataplatform.user.dto.UpdateUserStatusRequest;
import com.honortech.dataplatform.user.dto.UserResponse;
import com.honortech.dataplatform.user.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final SysUserService sysUserService;

    public AdminUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> listUsers() {
        return ApiResponse.success(sysUserService.listUsers());
    }

    @PostMapping
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("用户创建成功", sysUserService.createUser(request));
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("用户更新成功", sysUserService.updateUser(userId, request));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<UserResponse> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success("用户状态更新成功", sysUserService.updateUserStatus(userId, request.status()));
    }
}
