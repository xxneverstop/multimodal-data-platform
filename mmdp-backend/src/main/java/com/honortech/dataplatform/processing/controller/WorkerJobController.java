package com.honortech.dataplatform.processing.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.pipeline.dto.WorkerPipelineInfo;
import com.honortech.dataplatform.processing.dto.WorkerClaimRequest;
import com.honortech.dataplatform.processing.dto.WorkerClaimResponse;
import com.honortech.dataplatform.processing.dto.WorkerFailureRequest;
import com.honortech.dataplatform.processing.dto.WorkerSuccessRequest;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
import com.honortech.dataplatform.processing.service.WorkerPipelineRegistry;
import com.honortech.dataplatform.processing.util.PipelineIdNormalizer;
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
    public ApiResponse<WorkerClaimResponse> claimJob(
            @RequestBody(required = false) WorkerClaimRequest request) {
        String workerType = (request != null && request.workerType() != null)
                ? request.workerType().strip().toUpperCase() : "ALL";
        List<String> pipelineIds = request == null || request.pipelineIds() == null
                ? List.of()
                : request.pipelineIds().stream()
                        .map(PipelineIdNormalizer::normalize)
                        .toList();
        WorkerClaimResponse response = processingJobService.claimJob(workerType, pipelineIds);
        if (response == null) {
            return ApiResponse.success("No pending job for workerType=" + workerType, null);
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

    /** Worker 启动/心跳时上报全部 Pipeline 清单，Backend 按 workerType 分区缓存 */
    @PostMapping("/api/worker/pipelines/register")
    public ApiResponse<Void> registerPipelines(@RequestBody List<WorkerPipelineInfo> pipelines) {
        // 按 workerType 分组（null 或空默认为 CPU）
        var grouped = pipelines.stream()
                .collect(java.util.stream.Collectors.groupingBy(p ->
                        (p.workerType() != null && !p.workerType().isBlank())
                                ? p.workerType().strip().toUpperCase() : "CPU"));
        int total = 0;
        for (var entry : grouped.entrySet()) {
            List<WorkerPipelineInfo> normalized = entry.getValue().stream()
                    .map(p -> new WorkerPipelineInfo(
                            PipelineIdNormalizer.normalize(p.pipelineId()),
                            p.displayName(), p.description(), p.version(),
                            p.inputAssetTypes(), p.outputAssetTypes(),
                            p.runtimeDependencies(), entry.getKey()))
                    .toList();
            pipelineRegistry.replaceAll(entry.getKey(), normalized);
            total += normalized.size();
            log.info("[Worker注册] {} Worker 注册 {} 个 Pipeline", entry.getKey(), normalized.size());
        }
        return ApiResponse.success("registered " + total + " pipelines", null);
    }

    /** 查询当前 Worker 注册表（调试用） */
    @GetMapping("/api/worker/pipelines/registry")
    public ApiResponse<List<WorkerPipelineInfo>> getRegistry() {
        return ApiResponse.success(pipelineRegistry.getAll());
    }
}
