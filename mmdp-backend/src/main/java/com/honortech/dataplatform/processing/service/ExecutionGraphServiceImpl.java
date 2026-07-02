package com.honortech.dataplatform.processing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.processing.dto.ExecutionGraphResponse;
import com.honortech.dataplatform.processing.dto.ExecutionGraphResponse.ExecutionGraphEdge;
import com.honortech.dataplatform.processing.dto.ExecutionGraphResponse.ExecutionGraphNode;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import com.honortech.dataplatform.pipeline.mapper.PipelineDefinitionMapper;
import com.honortech.dataplatform.qc.entity.QcReport;
import com.honortech.dataplatform.qc.mapper.QcReportMapper;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExecutionGraphServiceImpl implements ExecutionGraphService {

    private final AcquisitionTaskService acquisitionTaskService;
    private final CollectionSessionMapper sessionMapper;
    private final DataAssetMapper dataAssetMapper;
    private final ProcessingJobMapper processingJobMapper;
    private final AssetLineageMapper assetLineageMapper;
    private final QcReportMapper qcReportMapper;
    private final PipelineDefinitionMapper pipelineDefMapper;

    public ExecutionGraphServiceImpl(
            AcquisitionTaskService acquisitionTaskService,
            CollectionSessionMapper sessionMapper,
            DataAssetMapper dataAssetMapper,
            ProcessingJobMapper processingJobMapper,
            AssetLineageMapper assetLineageMapper,
            QcReportMapper qcReportMapper,
            PipelineDefinitionMapper pipelineDefMapper) {
        this.acquisitionTaskService = acquisitionTaskService;
        this.sessionMapper = sessionMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.processingJobMapper = processingJobMapper;
        this.assetLineageMapper = assetLineageMapper;
        this.qcReportMapper = qcReportMapper;
        this.pipelineDefMapper = pipelineDefMapper;
    }

    @Override
    public ExecutionGraphResponse getTaskExecutionGraph(Long taskId) {
        // 1. 加载全部数据
        AcquisitionTask task = acquisitionTaskService.getTask(taskId);
        List<CollectionSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .eq(CollectionSession::getTaskId, taskId));
        List<DataAsset> assets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getTaskId, taskId));
        List<ProcessingJob> jobs = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getTaskId, taskId)
                        .orderByAsc(ProcessingJob::getCreatedAt));
        List<AssetLineage> lineages = assetLineageMapper.selectList(
                new LambdaQueryWrapper<AssetLineage>()
                        .eq(AssetLineage::getTaskId, taskId));
        List<QcReport> qcReports = qcReportMapper.selectList(
                new LambdaQueryWrapper<QcReport>()
                        .eq(QcReport::getTaskId, taskId));

        // 预加载 pipeline 显示名称
        Map<String, String> pipelineNames = loadPipelineNames(jobs);

        // 2. 构建节点
        Map<String, ExecutionGraphNode> nodeMap = new LinkedHashMap<>();
        List<ExecutionGraphEdge> edges = new ArrayList<>();

        // Task 根节点
        String taskNodeId = nodeId("task", task.getId());
        nodeMap.put(taskNodeId, new ExecutionGraphNode(
                taskNodeId, "task", task.getTaskName(),
                null, null, task.getStatus(), task.getId(), 0, null, null));

        // Session 节点 + Task→Session 边
        for (CollectionSession session : sessions) {
            String sNodeId = nodeId("session", session.getId());
            nodeMap.put(sNodeId, new ExecutionGraphNode(
                    sNodeId, "session", session.getSessionCode(),
                    null, null, session.getUploadStatus(), session.getId(), 0, null, null));
            edges.add(new ExecutionGraphEdge(taskNodeId, sNodeId, "contains", "solid"));
        }

        // Asset 节点
        for (DataAsset asset : assets) {
            String aNodeId = nodeId("asset", asset.getId());
            nodeMap.put(aNodeId, new ExecutionGraphNode(
                    aNodeId, "asset", asset.getDisplayName(),
                    asset.getAssetType(), null, null, asset.getId(), 0,
                    asset.getSourceType(), null));

            // 归属关系：Session → Asset 或 Task → Asset
            if (asset.getSessionId() != null && nodeMap.containsKey(nodeId("session", asset.getSessionId()))) {
                edges.add(new ExecutionGraphEdge(
                        nodeId("session", asset.getSessionId()), aNodeId, "contains", "solid"));
            } else if (asset.getProducedByJobId() == null) {
                // 无 session 且非 job 产出的资产直接挂 Task
                edges.add(new ExecutionGraphEdge(taskNodeId, aNodeId, "contains", "dashed"));
            }
        }

        // Job 节点
        for (ProcessingJob job : jobs) {
            String jNodeId = nodeId("job", job.getId());
            String displayLabel = pipelineNames.getOrDefault(job.getPipelineId(), job.getPipelineId());
            nodeMap.put(jNodeId, new ExecutionGraphNode(
                    jNodeId, "job", displayLabel,
                    null, job.getPipelineId(), job.getStatus(), job.getId(), 0,
                    null, job.getExecutorType()));
        }

        // QC 节点 + Asset→QC 边
        for (QcReport report : qcReports) {
            String qcNodeId = nodeId("qc", report.getId());
            String qcLabel = report.getQcType() != null ? report.getQcType() : "QC";
            nodeMap.put(qcNodeId, new ExecutionGraphNode(
                    qcNodeId, "qc", qcLabel,
                    null, null, report.getQcStatus(), report.getId(), 0, null, null));

            // QC 关联到对应的 file/asset
            if (report.getFileId() != null) {
                // 找到 fileId 对应的 asset
                for (DataAsset asset : assets) {
                    if (report.getFileId().equals(asset.getFileId())) {
                        edges.add(new ExecutionGraphEdge(
                                nodeId("asset", asset.getId()), qcNodeId, "checked_by", "dashed"));
                        break;
                    }
                }
            }
        }

        // 3. 构建边：AssetLineage 血缘
        for (AssetLineage lineage : lineages) {
            String srcAssetNode = nodeId("asset", lineage.getSourceAssetId());
            String tgtAssetNode = nodeId("asset", lineage.getTargetAssetId());
            String jobNode = nodeId("job", lineage.getJobId());

            if (nodeMap.containsKey(srcAssetNode) && nodeMap.containsKey(jobNode)) {
                edges.add(new ExecutionGraphEdge(srcAssetNode, jobNode, "input", "solid"));
            }
            if (nodeMap.containsKey(jobNode) && nodeMap.containsKey(tgtAssetNode)) {
                edges.add(new ExecutionGraphEdge(jobNode, tgtAssetNode, "output", "solid"));
            }
        }

        // 4. 隐式血缘：producedByJobId → Job→Asset 边（如果 AssetLineage 还没覆盖）
        Set<String> lineageCovered = new LinkedHashSet<>();
        for (AssetLineage lineage : lineages) {
            lineageCovered.add(jobAssetEdgeKey(lineage.getJobId(), lineage.getTargetAssetId()));
        }
        for (DataAsset asset : assets) {
            if (asset.getProducedByJobId() != null) {
                String jobNode = nodeId("job", asset.getProducedByJobId());
                String assetNode = nodeId("asset", asset.getId());
                String key = jobAssetEdgeKey(asset.getProducedByJobId(), asset.getId());
                if (nodeMap.containsKey(jobNode) && nodeMap.containsKey(assetNode)
                        && !lineageCovered.contains(key)) {
                    edges.add(new ExecutionGraphEdge(jobNode, assetNode, "output", "solid"));
                }
            }
        }

        // 5. dependsOnJobIds 显式依赖
        for (ProcessingJob job : jobs) {
            List<Long> depIds = parseDependsOnJobIds(job.getDependsOnJobIds());
            String targetJobNode = nodeId("job", job.getId());
            for (Long depId : depIds) {
                String sourceJobNode = nodeId("job", depId);
                if (nodeMap.containsKey(sourceJobNode) && nodeMap.containsKey(targetJobNode)) {
                    edges.add(new ExecutionGraphEdge(sourceJobNode, targetJobNode, "depends", "dashed"));
                }
            }
        }

        // 6. 拓扑排序分配 depth
        assignDepths(nodeMap, edges);

        return new ExecutionGraphResponse(new ArrayList<>(nodeMap.values()), edges);
    }

    @Override
    public ExecutionGraphResponse getSessionExecutionGraph(Long sessionId) {
        CollectionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return new ExecutionGraphResponse(List.of(), List.of());
        }
        // 通过 session 所属 taskId 获取全图，前端可按 session 过滤
        return getTaskExecutionGraph(session.getTaskId());
    }

    // ── 私有辅助方法 ──

    private String nodeId(String type, Long id) {
        return type + "-" + id;
    }

    private String jobAssetEdgeKey(Long jobId, Long assetId) {
        return "job-" + jobId + "->asset-" + assetId;
    }

    private Map<String, String> loadPipelineNames(List<ProcessingJob> jobs) {
        Map<String, String> names = new HashMap<>();
        for (ProcessingJob job : jobs) {
            String pid = job.getPipelineId();
            if (pid != null && !names.containsKey(pid)) {
                PipelineDefinition def = pipelineDefMapper.selectOne(
                        new LambdaQueryWrapper<PipelineDefinition>()
                                .eq(PipelineDefinition::getPipelineId, pid));
                names.put(pid, def != null ? def.getDisplayName() : pid);
            }
        }
        return names;
    }

    private List<Long> parseDependsOnJobIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            // 简单解析 JSON 数组如 "[1,2,3]"
            String trimmed = raw.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String inner = trimmed.substring(1, trimmed.length() - 1).trim();
                if (inner.isEmpty()) return List.of();
                List<Long> ids = new ArrayList<>();
                for (String part : inner.split(",")) {
                    ids.add(Long.parseLong(part.trim()));
                }
                return ids;
            }
        } catch (NumberFormatException ignored) {
            // 忽略无效值
        }
        return List.of();
    }

    /**
     * Kahn 拓扑排序，给每个节点分配 depth 层级。
     */
    private void assignDepths(Map<String, ExecutionGraphNode> nodeMap, List<ExecutionGraphEdge> edges) {
        // 构建邻接表和入度
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            adj.put(nodeId, new ArrayList<>());
            inDegree.put(nodeId, 0);
        }
        for (ExecutionGraphEdge edge : edges) {
            if (adj.containsKey(edge.source()) && adj.containsKey(edge.target())) {
                adj.get(edge.source()).add(edge.target());
                inDegree.merge(edge.target(), 1, Integer::sum);
            }
        }

        // BFS：从入度为0的节点开始
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int currentDepth = 0;
        while (!queue.isEmpty()) {
            List<String> nextQueue = new ArrayList<>();
            for (String nodeId : queue) {
                ExecutionGraphNode node = nodeMap.get(nodeId);
                if (node != null) {
                    // 用反射无法修改 record，重新 put
                    nodeMap.put(nodeId, new ExecutionGraphNode(
                            node.id(), node.type(), node.label(),
                            node.assetType(), node.pipelineId(), node.status(),
                            node.detailId(), currentDepth, node.sourceType(), node.executorType()));
                }
                for (String neighbor : adj.getOrDefault(nodeId, List.of())) {
                    int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                    if (newDegree == 0) {
                        nextQueue.add(neighbor);
                    }
                }
            }
            queue = nextQueue;
            currentDepth++;
        }

        // 剩余的孤立节点给予最后 depth
        for (Map.Entry<String, ExecutionGraphNode> entry : nodeMap.entrySet()) {
            ExecutionGraphNode node = entry.getValue();
            if (node.depth() == 0 && !"task".equals(node.type())) {
                nodeMap.put(entry.getKey(), new ExecutionGraphNode(
                        node.id(), node.type(), node.label(),
                        node.assetType(), node.pipelineId(), node.status(),
                        node.detailId(), currentDepth, node.sourceType(), node.executorType()));
            }
        }
    }
}
