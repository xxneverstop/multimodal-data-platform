package com.honortech.dataplatform.processing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.processing.dto.TaskLineageEdgeResponse;
import com.honortech.dataplatform.processing.dto.TaskLineageNodeResponse;
import com.honortech.dataplatform.processing.dto.TaskLineageResponse;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskLineageServiceImpl implements TaskLineageService {

    private final AcquisitionTaskService acquisitionTaskService;
    private final DataAssetService dataAssetService;
    private final ProcessingJobMapper processingJobMapper;
    private final AssetLineageMapper assetLineageMapper;

    public TaskLineageServiceImpl(
            AcquisitionTaskService acquisitionTaskService,
            DataAssetService dataAssetService,
            ProcessingJobMapper processingJobMapper,
            AssetLineageMapper assetLineageMapper) {
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataAssetService = dataAssetService;
        this.processingJobMapper = processingJobMapper;
        this.assetLineageMapper = assetLineageMapper;
    }

    @Override
    public TaskLineageResponse getTaskLineage(Long taskId) {
        AcquisitionTask task = acquisitionTaskService.getTask(taskId);
        List<DataAsset> assets = dataAssetService.listByTaskId(taskId);
        List<ProcessingJob> jobs = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getTaskId, taskId)
                        .orderByAsc(ProcessingJob::getCreatedAt)
        );
        List<AssetLineage> lineages = assetLineageMapper.selectList(
                new LambdaQueryWrapper<AssetLineage>()
                        .eq(AssetLineage::getTaskId, taskId)
                        .orderByAsc(AssetLineage::getCreatedAt)
        );

        Map<String, TaskLineageNodeResponse> nodes = new LinkedHashMap<>();
        List<TaskLineageEdgeResponse> edges = new ArrayList<>();

        nodes.put(taskNodeId(task.getId()), new TaskLineageNodeResponse(
                taskNodeId(task.getId()),
                "task",
                task.getTaskName(),
                null,
                null,
                task.getStatus(),
                task.getId()
        ));

        for (DataAsset asset : assets) {
            String assetNodeId = assetNodeId(asset.getId());
            nodes.put(assetNodeId, new TaskLineageNodeResponse(
                    assetNodeId,
                    "asset",
                    asset.getDisplayName(),
                    asset.getAssetType(),
                    null,
                    null,
                    asset.getId()
            ));
            if (asset.getProducedByJobId() == null) {
                edges.add(new TaskLineageEdgeResponse(taskNodeId(taskId), assetNodeId, "asset"));
            }
        }

        for (ProcessingJob job : jobs) {
            String jobNodeId = jobNodeId(job.getId());
            nodes.put(jobNodeId, new TaskLineageNodeResponse(
                    jobNodeId,
                    "job",
                    job.getPipelineId(),
                    null,
                    job.getPipelineId(),
                    job.getStatus(),
                    job.getId()
            ));
        }

        for (AssetLineage lineage : lineages) {
            edges.add(new TaskLineageEdgeResponse(
                    assetNodeId(lineage.getSourceAssetId()),
                    jobNodeId(lineage.getJobId()),
                    "input"
            ));
            edges.add(new TaskLineageEdgeResponse(
                    jobNodeId(lineage.getJobId()),
                    assetNodeId(lineage.getTargetAssetId()),
                    "output"
            ));
        }

        return new TaskLineageResponse(new ArrayList<>(nodes.values()), edges);
    }

    private String taskNodeId(Long taskId) {
        return "task-" + taskId;
    }

    private String assetNodeId(Long assetId) {
        return "asset-" + assetId;
    }

    private String jobNodeId(Long jobId) {
        return "job-" + jobId;
    }
}
