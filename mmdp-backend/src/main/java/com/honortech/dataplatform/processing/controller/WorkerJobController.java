package com.honortech.dataplatform.processing.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.pipeline.dto.WorkerPipelineInfo;
import com.honortech.dataplatform.processing.dto.WorkerClaimResponse;
import com.honortech.dataplatform.processing.dto.WorkerFailureRequest;
import com.honortech.dataplatform.processing.dto.WorkerSuccessRequest;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
import com.honortech.dataplatform.processing.service.WorkerPipelineRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class WorkerJobController {

    private static final Logger log = LoggerFactory.getLogger(WorkerJobController.class);

    private final ProcessingJobService processingJobService;
    private final WorkerPipelineRegistry pipelineRegistry;

    public WorkerJobController(ProcessingJobService processingJobService,
                               WorkerPipelineRegistry pipelineRegistry) {
        this.processingJobService = processingJobService;
        this.pipelineRegistry = pipelineRegistry;
    }

    @PostMapping("/api/worker/jobs/claim")
    public ApiResponse<WorkerClaimResponse> claimJob() {
        WorkerClaimResponse response = processingJobService.claimJob();
        if (response == null) {
            return ApiResponse.success("No pending job", null);
        }
        return ApiResponse.success("Job claimed", response);
    }

    @PostMapping("/api/worker/jobs/{jobId}/success")
    public ApiResponse<Void> reportSuccess(
            @PathVariable Long jobId,
            @Valid @RequestBody WorkerSuccessRequest request) {
        processingJobService.completeJob(jobId, request);
        return ApiResponse.success("Job completed", null);
    }

    @PostMapping("/api/worker/jobs/{jobId}/failure")
    public ApiResponse<Void> reportFailure(
            @PathVariable Long jobId,
            @Valid @RequestBody WorkerFailureRequest request) {
        processingJobService.failJob(jobId, request);
        return ApiResponse.success("Job failure recorded", null);
    }

    /** Worker 启动/心跳时上报全部 Pipeline 清单，Backend 缓存至内存注册表 */
    @PostMapping("/api/worker/pipelines/register")
    public ApiResponse<Void> registerPipelines(@RequestBody List<WorkerPipelineInfo> pipelines) {
        pipelineRegistry.replaceAll(pipelines);
        log.info("[Worker注册] 收到 {} 个 Pipeline 注册", pipelines.size());
        return ApiResponse.success("registered", null);
    }

    /** 查询当前 Worker 注册表（调试用） */
    @GetMapping("/api/worker/pipelines/registry")
    public ApiResponse<List<WorkerPipelineInfo>> getRegistry() {
        return ApiResponse.success(pipelineRegistry.getAll());
    }
}
