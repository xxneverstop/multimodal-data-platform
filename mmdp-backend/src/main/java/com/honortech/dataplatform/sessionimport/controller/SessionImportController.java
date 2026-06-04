package com.honortech.dataplatform.sessionimport.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.service.SessionImportService;
import com.honortech.dataplatform.sessionimport.service.SessionImportServiceImpl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SessionImportController {

    private final SessionImportService sessionImportService;

    public SessionImportController(SessionImportService sessionImportService) {
        this.sessionImportService = sessionImportService;
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
}
