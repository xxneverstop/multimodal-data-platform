package com.honortech.dataplatform.processing.dto;

import java.util.List;

/**
 * 执行图（DAG）响应，包含节点列表和边列表。
 * 前端可直接用 dagre 布局渲染。
 */
public record ExecutionGraphResponse(
        List<ExecutionGraphNode> nodes,
        List<ExecutionGraphEdge> edges
) {

    /**
     * 执行图中的节点，对应 Airflow DAG 中的一个 Task。
     */
    public record ExecutionGraphNode(
            String id,
            /** 节点类型：task | session | asset | job | qc */
            String type,
            /** 显示名称 */
            String label,
            /** 仅 asset 节点：资产类型 */
            String assetType,
            /** 仅 job 节点：pipelineId */
            String pipelineId,
            /** 状态：用于颜色映射 */
            String status,
            /** 实体ID，用于前端跳转 */
            Long detailId,
            /** 拓扑深度，dagre 渲染时作为 rank */
            Integer depth,
            /** 仅 asset 节点：资产来源类型 */
            String sourceType,
            /** 仅 job 节点：执行器类型 */
            String executorType
    ) {}

    /**
     * 执行图中的边，表示节点之间的溯源/依赖关系。
     */
    public record ExecutionGraphEdge(
            String source,
            String target,
            /** 关系标签：contains | input | output | depends | checked_by | produces */
            String label,
            /** 连线样式：solid | dashed */
            String style
    ) {}
}
