package com.honortech.dataplatform.pipeline.dto;

import java.util.List;

/**
 * Worker 端 Pipeline 元数据。
 * Worker 启动时主动向 Backend 注册，使用标准 camelCase JSON 字段名。
 */
public record WorkerPipelineInfo(
        String pipelineId,
        String displayName,
        String description,
        String version,
        List<String> inputAssetTypes,
        List<String> outputAssetTypes,
        List<String> runtimeDependencies
) {}
