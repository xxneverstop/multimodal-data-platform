package com.honortech.dataplatform.session.controller;

import com.honortech.dataplatform.session.service.CollectionSessionService;
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

class CollectionSessionControllerTest {

    @Test
    void shouldKeepLegacyImportEndpointWorking() throws Exception {
        CollectionSessionService collectionSessionService = Mockito.mock(CollectionSessionService.class);
        SessionImportService sessionImportService = Mockito.mock(SessionImportService.class);
        Mockito.when(sessionImportService.importSession(Mockito.any())).thenReturn(
                new SessionImportResponse(10L, 20L, 30L, "IMPORTED", false)
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new CollectionSessionController(collectionSessionService, sessionImportService)
        ).build();

        mockMvc.perform(multipart("/api/tasks/{taskId}/sessions/import", 20L)
                        .file(new MockMultipartFile("manifest", "manifest.json", "application/json", "{}".getBytes()))
                        .file(new MockMultipartFile("files", "imu.jsonl", "application/json", "{}".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importId").value(10L))
                .andExpect(jsonPath("$.data.platformSessionId").value(30L));
    }
}
