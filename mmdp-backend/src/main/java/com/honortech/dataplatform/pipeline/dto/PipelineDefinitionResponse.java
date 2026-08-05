package com.honortech.dataplatform.pipeline.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pipeline 定义响应，包含 session 维度的可用性信息。
 * latestJobStatus / isReady / blockedReason 在 session 级查询时填充，
 * 全局查询时均为 null。
 */
public record PipelineDefinitionResponse(
        Long id,
        String pipelineId,
        String displayName,
        String description,
        List<String> inputAssetTypes,
        List<String> outputAssetTypes,
        String executorType,
        Integer enabled,
        List<Long> profileIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 该 session 下最近一个 job 的状态，无 job 则为 null */
        String latestJobStatus,
        /** 当前是否可以提交处理任务 */
        boolean isReady,
        /** 不可提交时的阻塞原因 */
        String blockedReason
) {}
