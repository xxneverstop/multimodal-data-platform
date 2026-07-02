package com.honortech.dataplatform.pipeline.service;

import com.honortech.dataplatform.pipeline.dto.CreatePipelineRequest;
import com.honortech.dataplatform.pipeline.dto.PipelineDefinitionResponse;

import java.util.List;

public interface PipelineDefinitionService {

    List<PipelineDefinitionResponse> listPipelines();

    PipelineDefinitionResponse getPipeline(Long id);

    PipelineDefinitionResponse createPipeline(CreatePipelineRequest request);

    PipelineDefinitionResponse updatePipeline(Long id, CreatePipelineRequest request);

    void disablePipeline(Long id);

    void enablePipeline(Long id);

    /** 根据 session 的 profile + 已有资产，返回可用的 pipeline */
    List<PipelineDefinitionResponse> getAvailablePipelines(Long sessionId);
}
