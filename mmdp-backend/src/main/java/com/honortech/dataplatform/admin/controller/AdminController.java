package com.honortech.dataplatform.admin.controller;

import com.honortech.dataplatform.admin.dto.CleanupResult;
import com.honortech.dataplatform.admin.service.DataCleanupService;
import com.honortech.dataplatform.common.api.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DataCleanupService dataCleanupService;

    public AdminController(DataCleanupService dataCleanupService) {
        this.dataCleanupService = dataCleanupService;
    }

    @DeleteMapping("/processing-jobs/{jobId}/outputs")
    public ApiResponse<CleanupResult> deleteJobOutputs(@PathVariable Long jobId) {
        CleanupResult result = dataCleanupService.deleteProcessingJobOutputs(jobId);
        return ApiResponse.success(result.getSummary(), result);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<CleanupResult> deleteSession(@PathVariable Long sessionId) {
        CleanupResult result = dataCleanupService.deleteSession(sessionId);
        return ApiResponse.success(result.getSummary(), result);
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<CleanupResult> deleteTask(@PathVariable Long taskId) {
        CleanupResult result = dataCleanupService.deleteTask(taskId);
        return ApiResponse.success(result.getSummary(), result);
    }
}
