package com.honortech.dataplatform.processing.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.processing.dto.AvailablePipelineResponse;
import com.honortech.dataplatform.processing.service.PipelineAvailabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/available-pipelines")
public class PipelineController {

    private final PipelineAvailabilityService pipelineAvailabilityService;

    public PipelineController(PipelineAvailabilityService pipelineAvailabilityService) {
        this.pipelineAvailabilityService = pipelineAvailabilityService;
    }

    @GetMapping
    public ApiResponse<List<AvailablePipelineResponse>> listAvailablePipelines(@PathVariable Long taskId) {
        return ApiResponse.success(pipelineAvailabilityService.listAvailablePipelines(taskId));
    }
}
