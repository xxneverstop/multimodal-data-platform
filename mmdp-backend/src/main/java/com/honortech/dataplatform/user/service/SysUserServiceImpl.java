package com.honortech.dataplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.user.dto.CreateUserRequest;
import com.honortech.dataplatform.user.dto.CurrentUserResponse;
import com.honortech.dataplatform.user.dto.UpdateUserRequest;
import com.honortech.dataplatform.user.dto.UserResponse;
import com.honortech.dataplatform.user.entity.SysUser;
import com.honortech.dataplatform.user.enums.UserRoleCode;
import com.honortech.dataplatform.user.enums.UserStatus;
import com.honortech.dataplatform.user.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public CurrentUserResponse login(String username, String password) {
        SysUser user = findByUsername(username);
        if (user == null || !passwordEncoder.matches(trimToEmpty(password), user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BizException("当前用户已被停用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return toCurrentUserResponse(user);
    }

    @Override
    public List<UserResponse> listUsers() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .orderByDesc(SysUser::getCreatedAt)
                        .orderByDesc(SysUser::getId))
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        if (findByUsername(username) != null) {
            throw new BizException("登录账号已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        user.setDisplayName(trimRequired(request.displayName(), "displayName"));
        user.setRoleCode(normalizeRoleCode(request.roleCode()));
        user.setIsAdmin(Boolean.TRUE.equals(request.isAdmin()) ? 1 : 0);
        user.setStatus(normalizeStatus(request.status()));
        user.setPhone(trimToNull(request.phone()));
        user.setEmail(trimToNull(request.email()));
        user.setRemark(trimToNull(request.remark()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        sysUserMapper.insert(user);
        return toUserResponse(user);
    }

    @Override
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        SysUser user = getRequiredById(userId);
        user.setDisplayName(trimRequired(request.displayName(), "displayName"));
        user.setRoleCode(normalizeRoleCode(request.roleCode()));
        user.setIsAdmin(Boolean.TRUE.equals(request.isAdmin()) ? 1 : 0);
        user.setStatus(normalizeStatus(request.status()));
        user.setPhone(trimToNull(request.phone()));
        user.setEmail(trimToNull(request.email()));
        user.setRemark(trimToNull(request.remark()));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return toUserResponse(user);
    }

    @Override
    public UserResponse updateUserStatus(Long userId, String status) {
        SysUser user = getRequiredById(userId);
        user.setStatus(normalizeStatus(status));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return toUserResponse(user);
    }

    @Override
    public SysUser getRequiredById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在: " + userId);
        }
        return user;
    }

    private SysUser findByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, normalizedUsername)
                .last("LIMIT 1"));
    }

    private CurrentUserResponse toCurrentUserResponse(SysUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleCode(),
                isAdmin(user)
        );
    }

    private UserResponse toUserResponse(SysUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleCode(),
                isAdmin(user),
                user.getStatus(),
                user.getPhone(),
                user.getEmail(),
                user.getRemark(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private boolean isAdmin(SysUser user) {
        return user.getIsAdmin() != null && user.getIsAdmin() == 1;
    }

    private String normalizeUsername(String value) {
        String normalized = trimRequired(value, "username").toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new BizException("username 长度不能超过64");
        }
        return normalized;
    }

    private String normalizeRoleCode(String value) {
        String normalized = UserRoleCode.normalize(value);
        if (!UserRoleCode.isValid(normalized)) {
            throw new BizException("roleCode 非法");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = UserStatus.normalize(value);
        if (!UserStatus.isValid(normalized)) {
            throw new BizException("status 非法");
        }
        return normalized;
    }

    private String trimRequired(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(fieldName + " 不能为空");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
