package com.honortech.dataplatform.sessionimport.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportRequest;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportResponse;
import com.honortech.dataplatform.sessionimport.dto.InitiateImportUploadRequest;
import com.honortech.dataplatform.sessionimport.dto.InitiateImportUploadResponse;
import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.service.SessionImportService;
import com.honortech.dataplatform.sessionimport.service.SessionImportServiceImpl;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SessionImportController {

    private final SessionImportService sessionImportService;

    public SessionImportController(SessionImportService sessionImportService) {
        this.sessionImportService = sessionImportService;
    }

    @PostMapping("/api/tasks/{taskId}/session-imports/uploads/initiate")
    public ApiResponse<InitiateImportUploadResponse> initiateImportUpload(
            @PathVariable Long taskId,
            @Valid @RequestBody InitiateImportUploadRequest request) {
        return ApiResponse.success("Session import upload initiated", sessionImportService.initiateImportUpload(taskId, request));
    }

    @PostMapping("/api/session-imports")
    public ApiResponse<SessionImportResponse> importSessionPackage(
            @RequestPart("manifest") MultipartFile manifest,
            @RequestPart("archive") MultipartFile archive) {
        SessionImportResponse response = sessionImportService.importSession(
                new SessionImportRequestContext(
                        SessionImportServiceImpl.SOURCE_ENDPOINT_SESSION_IMPORTS,
                        null,
                        manifest,
                        archive,
                        null
                )
        );
        return ApiResponse.success(response.existing() ? "Session already imported" : "Session imported", response);
    }

    @PostMapping("/api/session-imports/finalize")
    public ApiResponse<FinalizeSessionImportResponse> finalizeImport(
            @Valid @RequestBody FinalizeSessionImportRequest request) {
        FinalizeSessionImportResponse response = sessionImportService.finalizeImport(request);
        return ApiResponse.success(response.existing() ? "Session already imported" : "Session imported", response);
    }
}
