package com.honortech.dataplatform.processing.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.processing.dto.CreateManualProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateSessionJobRequest;
import com.honortech.dataplatform.processing.dto.ManualProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.TaskLineageResponse;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
import com.honortech.dataplatform.processing.service.TaskLineageService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
public class ProcessingJobController {

    private final ProcessingJobService processingJobService;
    private final TaskLineageService taskLineageService;

    public ProcessingJobController(ProcessingJobService processingJobService, TaskLineageService taskLineageService) {
        this.processingJobService = processingJobService;
        this.taskLineageService = taskLineageService;
    }

    @PostMapping("/api/tasks/{taskId}/processing-jobs")
    public ApiResponse<ProcessingJobResponse> createJob(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateProcessingJobRequest request) {
        return ApiResponse.success("Processing job created", processingJobService.createJob(taskId, request));
    }

    @PostMapping("/api/tasks/{taskId}/processing-jobs/manual")
    public ApiResponse<ManualProcessingJobResponse> createManualJob(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateManualProcessingJobRequest request) {
        return ApiResponse.success("Manual processing job registered", processingJobService.createManualJob(taskId, request));
    }

    @GetMapping("/api/tasks/{taskId}/processing-jobs")
    public ApiResponse<List<ProcessingJobResponse>> listJobs(@PathVariable Long taskId) {
        return ApiResponse.success(processingJobService.listJobsByTaskId(taskId));
    }

    @GetMapping("/api/tasks/{taskId}/lineage")
    public ApiResponse<TaskLineageResponse> getTaskLineage(@PathVariable Long taskId) {
        return ApiResponse.success(taskLineageService.getTaskLineage(taskId));
    }

    @GetMapping("/api/processing-jobs/{jobId}")
    public ApiResponse<ProcessingJobResponse> getJob(@PathVariable Long jobId) {
        return ApiResponse.success(processingJobService.getJob(jobId));
    }

    @PostMapping("/api/sessions/{sessionId}/processing-jobs")
    public ApiResponse<ProcessingJobResponse> createSessionJob(
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateSessionJobRequest request) {
        return ApiResponse.success("Processing job created",
                processingJobService.createSessionJob(sessionId, request));
    }

    @GetMapping("/api/sessions/{sessionId}/processing-jobs")
    public ApiResponse<List<ProcessingJobResponse>> listSessionJobs(@PathVariable Long sessionId) {
        return ApiResponse.success(processingJobService.listJobsBySessionId(sessionId));
    }
}
