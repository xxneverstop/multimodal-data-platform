package com.honortech.dataplatform.session.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.session.dto.SessionListItemResponse;
import com.honortech.dataplatform.session.service.CollectionSessionService;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.service.SessionImportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CollectionSessionControllerTest {

    @Test
    void shouldReturnCollectorAndUploadedAtInSessionList() throws Exception {
        CollectionSessionService collectionSessionService = Mockito.mock(CollectionSessionService.class);
        SessionImportService sessionImportService = Mockito.mock(SessionImportService.class);
        SessionListItemResponse item = new SessionListItemResponse();
        item.setId(30L);
        item.setSessionId("session-001");
        item.setCollectorName("采集端 A");
        item.setUploadedAt(LocalDateTime.of(2026, 6, 15, 10, 30));
        Page<SessionListItemResponse> page = new Page<>(1, 20, 1);
        page.setRecords(java.util.List.of(item));
        Mockito.when(collectionSessionService.listPage(Mockito.any())).thenReturn(page);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new CollectionSessionController(collectionSessionService, sessionImportService)
        ).build();

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].collectorName").value("采集端 A"))
                .andExpect(jsonPath("$.data.records[0].uploadedAt[0]").value(2026))
                .andExpect(jsonPath("$.data.records[0].uploadedAt[4]").value(30));
    }

    @Test
    void shouldReturnFallbackCollectorAndUploadedAtInSessionList() throws Exception {
        CollectionSessionService collectionSessionService = Mockito.mock(CollectionSessionService.class);
        SessionImportService sessionImportService = Mockito.mock(SessionImportService.class);
        SessionListItemResponse item = new SessionListItemResponse();
        item.setId(31L);
        item.setSessionId("manual-session-001");
        item.setCollectorName("手动上传");
        item.setUploadedAt(LocalDateTime.of(2026, 6, 15, 11, 0));
        Page<SessionListItemResponse> page = new Page<>(1, 20, 1);
        page.setRecords(java.util.List.of(item));
        Mockito.when(collectionSessionService.listPage(Mockito.any())).thenReturn(page);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new CollectionSessionController(collectionSessionService, sessionImportService)
        ).build();

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].collectorName").value("手动上传"))
                .andExpect(jsonPath("$.data.records[0].uploadedAt[0]").value(2026))
                .andExpect(jsonPath("$.data.records[0].uploadedAt[3]").value(11));
    }

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
