package com.honortech.dataplatform.sessionimport.controller;

import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportResponse;
import com.honortech.dataplatform.sessionimport.dto.InitiateImportUploadResponse;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.service.SessionImportService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void shouldInitiateDirectoryImportUpload() throws Exception {
        SessionImportService service = Mockito.mock(SessionImportService.class);
        Mockito.when(service.initiateImportUpload(Mockito.eq(12L), Mockito.any())).thenReturn(
                new InitiateImportUploadResponse(
                        "mmdp-bucket",
                        "cn-hangzhou",
                        "oss-cn-hangzhou.aliyuncs.com",
                        "imports/12/imp-001/manifest.json",
                        "ak",
                        "sk",
                        "token",
                        "2026-06-05T12:00:00Z"
                )
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SessionImportController(service)).build();

        mockMvc.perform(post("/api/tasks/12/session-imports/uploads/initiate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "fileName": "manifest.json",
                                  "relativePath": "manifest.json",
                                  "importKey": "imp-001",
                                  "fileSize": 123,
                                  "contentType": "application/json"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.objectKey").value("imports/12/imp-001/manifest.json"));
    }

    @Test
    void shouldFinalizeDirectoryImport() throws Exception {
        SessionImportService service = Mockito.mock(SessionImportService.class);
        Mockito.when(service.finalizeImport(Mockito.any())).thenReturn(
                new FinalizeSessionImportResponse(5L, 2L, 3L, "SESS-001", "LS-001", "PROFILE-A", "S-001", "IMPORTED", false, 4, 2, 2)
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SessionImportController(service)).build();

        mockMvc.perform(post("/api/session-imports/finalize")
                        .contentType("application/json")
                        .content("""
                                {
                                  "taskId": 2,
                                  "importKey": "imp-001",
                                  "requestId": "imp-001",
                                  "manifest": {
                                    "localRefs": {"localSessionId": "LS-001"},
                                    "task": {"profileCode": "PROFILE-A"},
                                    "subject": {"code": "S-001"},
                                    "action": {"name": "Walk"},
                                    "session": {"startedAt": "2026-06-05T10:20:30", "timestampPolicy": "device"},
                                    "sources": {
                                      "cam01": {"path": "sources/cam01/video.mp4"}
                                    }
                                  },
                                  "uploadedFiles": [
                                    {
                                      "originalFilename": "manifest.json",
                                      "relativePath": "manifest.json",
                                      "objectKey": "imports/2/imp-001/manifest.json",
                                      "contentType": "application/json",
                                      "fileSize": 200
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platformSessionCode").value("SESS-001"))
                .andExpect(jsonPath("$.data.createdAssetCount").value(2));
    }
}
