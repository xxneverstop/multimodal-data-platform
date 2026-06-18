package com.honortech.dataplatform.processing.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.processing.dto.WorkerClaimResponse;
import com.honortech.dataplatform.processing.dto.WorkerFailureRequest;
import com.honortech.dataplatform.processing.dto.WorkerSuccessRequest;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worker/jobs")
@Validated
public class WorkerJobController {

    private final ProcessingJobService processingJobService;

    public WorkerJobController(ProcessingJobService processingJobService) {
        this.processingJobService = processingJobService;
    }

    @PostMapping("/claim")
    public ApiResponse<WorkerClaimResponse> claimJob() {
        WorkerClaimResponse response = processingJobService.claimJob();
        if (response == null) {
            return ApiResponse.success("No pending job", null);
        }
        return ApiResponse.success("Job claimed", response);
    }

    @PostMapping("/{jobId}/success")
    public ApiResponse<Void> reportSuccess(
            @PathVariable Long jobId,
            @Valid @RequestBody WorkerSuccessRequest request) {
        processingJobService.completeJob(jobId, request);
        return ApiResponse.success("Job completed", null);
    }

    @PostMapping("/{jobId}/failure")
    public ApiResponse<Void> reportFailure(
            @PathVariable Long jobId,
            @Valid @RequestBody WorkerFailureRequest request) {
        processingJobService.failJob(jobId, request);
        return ApiResponse.success("Job failure recorded", null);
    }
}
