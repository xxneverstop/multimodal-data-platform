package com.honortech.dataplatform.user.controller;

import com.honortech.dataplatform.common.auth.AuthInterceptor;
import com.honortech.dataplatform.common.auth.AuthSessionUser;
import com.honortech.dataplatform.common.auth.AuthSessionUtils;
import com.honortech.dataplatform.common.exception.GlobalExceptionHandler;
import com.honortech.dataplatform.user.dto.UserResponse;
import com.honortech.dataplatform.user.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    @Test
    void shouldRejectListUsersWhenUnauthenticated() throws Exception {
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(sysUserService))
                .addInterceptors(new AuthInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectListUsersWhenNotAdmin() throws Exception {
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(sysUserService))
                .addInterceptors(new AuthInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthSessionUtils.SESSION_USER_KEY,
                new AuthSessionUser(3L, "annotator", "标注员乙", "ANNOTATOR", false)
        );

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldAllowAdminToListUsers() throws Exception {
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        Mockito.when(sysUserService.listUsers()).thenReturn(List.of(
                new UserResponse(
                        1L,
                        "admin",
                        "平台管理员",
                        "ADMIN",
                        true,
                        "ACTIVE",
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 6, 10, 10, 30),
                        LocalDateTime.of(2026, 6, 1, 9, 0),
                        LocalDateTime.of(2026, 6, 10, 10, 30)
                )
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(sysUserService))
                .addInterceptors(new AuthInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthSessionUtils.SESSION_USER_KEY,
                new AuthSessionUser(1L, "admin", "平台管理员", "ADMIN", true)
        );

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].username").value("admin"))
                .andExpect(jsonPath("$.data[0].isAdmin").value(true));
    }
}
