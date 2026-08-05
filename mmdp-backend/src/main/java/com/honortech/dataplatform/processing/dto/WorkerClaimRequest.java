package com.honortech.dataplatform.processing.dto;

/**
 * Worker 领取任务时的请求体。
 * workerType 标识 Worker 类型（CPU/GPU），Backend 据此只下发匹配的 Pipeline Job。
 */
public record WorkerClaimRequest(String workerType) {}
