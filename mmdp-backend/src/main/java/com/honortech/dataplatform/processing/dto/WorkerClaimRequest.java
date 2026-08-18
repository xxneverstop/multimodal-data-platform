package com.honortech.dataplatform.processing.dto;

import java.util.List;

/**
 * Worker 领取任务时的请求体。
 * workerType 标识 Worker 类型（CPU/GPU），pipelineIds 标识当前进程实际支持的 Pipeline。
 */
public record WorkerClaimRequest(String workerType, List<String> pipelineIds) {}
