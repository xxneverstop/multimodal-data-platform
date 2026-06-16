package com.honortech.dataplatform.user.service;

import com.honortech.dataplatform.user.dto.CreateUserRequest;
import com.honortech.dataplatform.user.dto.CurrentUserResponse;
import com.honortech.dataplatform.user.dto.UpdateUserRequest;
import com.honortech.dataplatform.user.dto.UserResponse;
import com.honortech.dataplatform.user.entity.SysUser;

import java.util.List;

public interface SysUserService {

    CurrentUserResponse login(String username, String password);

    List<UserResponse> listUsers();

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(Long userId, UpdateUserRequest request);

    UserResponse updateUserStatus(Long userId, String status);

    SysUser getRequiredById(Long userId);
}
