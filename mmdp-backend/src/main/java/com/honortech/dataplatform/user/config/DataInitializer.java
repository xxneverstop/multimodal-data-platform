package com.honortech.dataplatform.user.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.user.entity.SysUser;
import com.honortech.dataplatform.user.enums.UserRoleCode;
import com.honortech.dataplatform.user.enums.UserStatus;
import com.honortech.dataplatform.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 首次启动时自动创建默认管理员账号，解决初始无用户可登录的问题。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123123123";
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "系统管理员";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<>());
        if (userCount > 0) {
            log.info("[init] 已有 {} 个用户，跳过默认管理员创建", userCount);
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setDisplayName(DEFAULT_ADMIN_DISPLAY_NAME);
        admin.setRoleCode(UserRoleCode.ADMIN.name());
        admin.setIsAdmin(1);
        admin.setStatus(UserStatus.ACTIVE.name());
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        admin.setDeleted(0);

        sysUserMapper.insert(admin);
        log.info("[init] 已创建默认管理员账号: {} / {}", DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }
}
