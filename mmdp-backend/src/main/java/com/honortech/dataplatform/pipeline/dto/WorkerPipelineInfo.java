package com.honortech.dataplatform.pipeline.dto;

import java.util.List;

/**
 * Worker 端 Pipeline 元数据。
 * 对应 Python Worker 端 pipeline-manifest.json 的每一项（snake_case 字段名）。
 * 反序列化由 WorkerManifestService 使用 SNAKE_CASE 策略处理。
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
