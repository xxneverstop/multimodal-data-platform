package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.processing.dto.ExecutionGraphResponse;

/**
 * 执行图（DAG）服务，参考 Airflow DAG 可视化理念。
 * 从 AssetLineage、ProcessingJob、DataAsset 等关系数据构建带拓扑排序的有向图。
 */
public interface ExecutionGraphService {

    /**
     * 获取某个采集任务的执行图（DAG），包含 Task → Session → Asset → QC → Job → 派生Asset 的完整链路。
     */
    ExecutionGraphResponse getTaskExecutionGraph(Long taskId);

    /**
     * 获取某个采集会话的执行图，以 Session 为根节点。
     */
    ExecutionGraphResponse getSessionExecutionGraph(Long sessionId);
}
