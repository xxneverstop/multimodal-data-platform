package com.honortech.dataplatform.sessionimport.controller;

import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.service.SessionImportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionImportControllerTest {

    @Test
    void shouldAcceptSessionImportPackage() throws Exception {
        SessionImportService service = Mockito.mock(SessionImportService.class);
        Mockito.when(service.importSession(Mockito.any())).thenReturn(
                new SessionImportResponse(1L, 2L, 3L, "IMPORTED", false)
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SessionImportController(service)).build();

        mockMvc.perform(multipart("/api/session-imports")
                        .file(new MockMultipartFile("manifest", "manifest.json", "application/json", "{}".getBytes()))
                        .file(new MockMultipartFile("archive", "session.zip", "application/zip", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importId").value(1L))
                .andExpect(jsonPath("$.data.platformTaskId").value(2L));
    }
}
