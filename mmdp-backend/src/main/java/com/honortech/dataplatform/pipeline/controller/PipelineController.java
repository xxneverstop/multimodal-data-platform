package com.honortech.dataplatform.pipeline.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.pipeline.dto.CreatePipelineRequest;
import com.honortech.dataplatform.pipeline.dto.PipelineDefinitionResponse;
import com.honortech.dataplatform.pipeline.service.PipelineDefinitionService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
@Validated
public class PipelineController {

    private final PipelineDefinitionService pipelineDefinitionService;

    public PipelineController(PipelineDefinitionService pipelineDefinitionService) {
        this.pipelineDefinitionService = pipelineDefinitionService;
    }

    @GetMapping
    public ApiResponse<List<PipelineDefinitionResponse>> listPipelines() {
        return ApiResponse.success(pipelineDefinitionService.listPipelines());
    }

    @GetMapping("/{id}")
    public ApiResponse<PipelineDefinitionResponse> getPipeline(@PathVariable Long id) {
        return ApiResponse.success(pipelineDefinitionService.getPipeline(id));
    }

    @PostMapping
    public ApiResponse<PipelineDefinitionResponse> createPipeline(
            @Valid @RequestBody CreatePipelineRequest request) {
        return ApiResponse.success("Pipeline创建成功",
                pipelineDefinitionService.createPipeline(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PipelineDefinitionResponse> updatePipeline(
            @PathVariable Long id,
            @Valid @RequestBody CreatePipelineRequest request) {
        return ApiResponse.success("Pipeline更新成功",
                pipelineDefinitionService.updatePipeline(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> disablePipeline(@PathVariable Long id) {
        pipelineDefinitionService.disablePipeline(id);
        return ApiResponse.success("Pipeline已禁用", null);
    }

    @GetMapping("/available/{sessionId}")
    public ApiResponse<List<PipelineDefinitionResponse>> getAvailablePipelines(
            @PathVariable Long sessionId) {
        return ApiResponse.success(pipelineDefinitionService.getAvailablePipelines(sessionId));
    }
}
