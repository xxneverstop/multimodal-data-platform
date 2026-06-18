package com.honortech.dataplatform.session.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.dto.PageResponse;
import com.honortech.dataplatform.session.dto.SessionListItemResponse;
import com.honortech.dataplatform.session.dto.SessionListQueryRequest;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import com.honortech.dataplatform.session.dto.SessionResponse;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.service.CollectionSessionService;
import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.service.SessionImportService;
import com.honortech.dataplatform.sessionimport.service.SessionImportServiceImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class CollectionSessionController {

    private final CollectionSessionService sessionService;
    private final SessionImportService sessionImportService;

    public CollectionSessionController(CollectionSessionService sessionService, SessionImportService sessionImportService) {
        this.sessionService = sessionService;
        this.sessionImportService = sessionImportService;
    }

    @Deprecated
    @PostMapping("/api/tasks/{taskId}/sessions/import")
    public ApiResponse<SessionImportResponse> importSession(
            @PathVariable Long taskId,
            @RequestPart("manifest") MultipartFile manifest,
            @RequestPart("files") List<MultipartFile> files) {
        SessionImportResponse response = sessionImportService.importSession(
                new SessionImportRequestContext(
                        SessionImportServiceImpl.SOURCE_ENDPOINT_LEGACY_TASK_ROUTE,
                        taskId,
                        manifest,
                        null,
                        files
                )
        );
        return ApiResponse.success(response.existing() ? "Session already imported" : "Session imported", response);
    }

    @GetMapping("/api/sessions")
    public ApiResponse<PageResponse<SessionListItemResponse>> listAllSessions(SessionListQueryRequest request) {
        var page = sessionService.listPage(request);
        return ApiResponse.success(PageResponse.of(page, page.getRecords()));
    }

    @GetMapping("/api/tasks/{taskId}/sessions")
    public ApiResponse<List<SessionResponse>> listTaskSessions(@PathVariable Long taskId) {
        return ApiResponse.success(sessionService.listByTaskId(taskId));
    }

    @GetMapping("/api/sessions/{sessionId}")
    public ApiResponse<SessionResponse> getSession(@PathVariable String sessionId) {
        CollectionSession session = sessionService.getBySessionId(sessionId);
        List<SessionResponse> list = sessionService.listByTaskId(session.getTaskId());
        SessionResponse found = list.stream()
                .filter(s -> s.sessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
        return ApiResponse.success(found);
    }

    @GetMapping("/api/sessions/{sessionId}/playback")
    public ApiResponse<SessionPlaybackResponse> getPlaybackData(
            @PathVariable String sessionId,
            @RequestParam(required = false) Long jobId) {
        return ApiResponse.success(sessionService.getPlaybackData(sessionId, jobId));
    }

    @GetMapping("/api/sessions/{sessionId}/playback/check")
    public ApiResponse<Boolean> checkPlayback(
            @PathVariable String sessionId,
            @RequestParam(required = false) Long jobId) {
        return ApiResponse.success(sessionService.canPlay(sessionId, jobId));
    }
}
