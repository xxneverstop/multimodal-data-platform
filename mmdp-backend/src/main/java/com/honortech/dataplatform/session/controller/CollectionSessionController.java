package com.honortech.dataplatform.session.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import com.honortech.dataplatform.session.dto.SessionResponse;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.service.CollectionSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class CollectionSessionController {

    private final CollectionSessionService sessionService;

    public CollectionSessionController(CollectionSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/api/tasks/{taskId}/sessions/import")
    public ApiResponse<SessionResponse> importSession(
            @PathVariable Long taskId,
            @RequestPart("manifest") MultipartFile manifest,
            @RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.success("Session imported", sessionService.importSession(taskId, manifest, files));
    }

    @GetMapping("/api/sessions")
    public ApiResponse<List<SessionResponse>> listAllSessions() {
        return ApiResponse.success(sessionService.listAll());
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
    public ApiResponse<SessionPlaybackResponse> getPlaybackData(@PathVariable String sessionId) {
        return ApiResponse.success(sessionService.getPlaybackData(sessionId));
    }
}
