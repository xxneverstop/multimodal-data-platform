package com.honortech.dataplatform.user.controller;

import com.honortech.dataplatform.common.auth.AuthSessionUser;
import com.honortech.dataplatform.common.auth.AuthSessionUtils;
import com.honortech.dataplatform.common.exception.GlobalExceptionHandler;
import com.honortech.dataplatform.user.dto.CurrentUserResponse;
import com.honortech.dataplatform.user.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Test
    void shouldLoginAndWriteSession() throws Exception {
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        Mockito.when(sysUserService.login(eq("admin"), eq("secret123"))).thenReturn(
                new CurrentUserResponse(1L, "admin", "平台管理员", "ADMIN", true)
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(sysUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.isAdmin").value(true));
    }

    @Test
    void shouldRejectMeWhenUnauthenticated() throws Exception {
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(sysUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnCurrentUserFromSession() throws Exception {
        SysUserService sysUserService = Mockito.mock(SysUserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(sysUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                AuthSessionUtils.SESSION_USER_KEY,
                new AuthSessionUser(2L, "collector", "采集员甲", "COLLECTOR", false)
        );

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleCode").value("COLLECTOR"))
                .andExpect(jsonPath("$.data.isAdmin").value(false));
    }
}
