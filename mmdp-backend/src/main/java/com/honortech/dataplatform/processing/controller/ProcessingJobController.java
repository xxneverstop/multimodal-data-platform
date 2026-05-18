package com.honortech.dataplatform.processing.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
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

    public ProcessingJobController(ProcessingJobService processingJobService) {
        this.processingJobService = processingJobService;
    }

    @PostMapping("/api/tasks/{taskId}/processing-jobs")
    public ApiResponse<ProcessingJobResponse> createJob(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateProcessingJobRequest request) {
        return ApiResponse.success("Processing job created", processingJobService.createJob(taskId, request));
    }

    @GetMapping("/api/tasks/{taskId}/processing-jobs")
    public ApiResponse<List<ProcessingJobResponse>> listJobs(@PathVariable Long taskId) {
        return ApiResponse.success(processingJobService.listJobsByTaskId(taskId));
    }

    @GetMapping("/api/processing-jobs/{jobId}")
    public ApiResponse<ProcessingJobResponse> getJob(@PathVariable Long jobId) {
        return ApiResponse.success(processingJobService.getJob(jobId));
    }
}
