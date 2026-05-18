package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.processing.dto.AvailablePipelineResponse;

import java.util.List;

public interface PipelineAvailabilityService {

    List<AvailablePipelineResponse> listAvailablePipelines(Long taskId);
}
