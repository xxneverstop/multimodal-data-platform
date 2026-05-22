package com.honortech.dataplatform.processing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetLineageRelationType;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.ProcessingExecutorType;
import com.honortech.dataplatform.common.enums.ProcessingJobStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.processing.dto.CreateManualProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.ManualProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ProcessingJobServiceImpl implements ProcessingJobService {

    private static final Set<AssetType> REQUIRED_ASSETS = Set.of(AssetType.MOCAP_CSV, AssetType.SMPL_RESULT);

    private final ProcessingJobMapper processingJobMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final DataAssetService dataAssetService;
    private final ProcessingJobExecutor processingJobExecutor;
    private final AssetLineageMapper assetLineageMapper;
    private final ObjectMapper objectMapper;

    public ProcessingJobServiceImpl(
            ProcessingJobMapper processingJobMapper,
            AcquisitionTaskService acquisitionTaskService,
            DataAssetService dataAssetService,
            ProcessingJobExecutor processingJobExecutor,
            AssetLineageMapper assetLineageMapper,
            ObjectMapper objectMapper) {
        this.processingJobMapper = processingJobMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataAssetService = dataAssetService;
        this.processingJobExecutor = processingJobExecutor;
        this.assetLineageMapper = assetLineageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ProcessingJobResponse createJob(Long taskId, CreateProcessingJobRequest request) {
        acquisitionTaskService.getTask(taskId);
        if (!PipelineIds.RGB_MOCAP_ALIGNMENT.equals(request.pipelineId())) {
            throw new BizException("Unsupported pipelineId: " + request.pipelineId());
        }

        List<DataAsset> assets = dataAssetService.listByTaskId(taskId);
        List<String> missingAssets = REQUIRED_ASSETS.stream()
                .filter(required -> assets.stream().noneMatch(asset -> required.name().equals(asset.getAssetType())))
                .map(Enum::name)
                .sorted()
                .toList();
        if (!missingAssets.isEmpty()) {
            throw new BizException("Missing required assets: " + String.join(", ", missingAssets));
        }

        ProcessingJob job = new ProcessingJob();
        job.setTaskId(taskId);
        job.setPipelineId(request.pipelineId());
        job.setExecutorType(ProcessingExecutorType.MOCK.name());
        job.setStatus(ProcessingJobStatus.CREATED.name());
        job.setParametersJson(writeJson(request.parameters()));
        job.setParamsJson(writeJson(request.parameters()));
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.insert(job);

        return toResponse(processingJobExecutor.execute(job, assets));
    }

    @Override
    @Transactional
    public ManualProcessingJobResponse createManualJob(Long taskId, CreateManualProcessingJobRequest request) {
        acquisitionTaskService.getTask(taskId);
        validatePipelineId(request.pipelineId());
        List<DataAsset> allAssets = dataAssetService.listByTaskId(taskId);
        List<DataAsset> inputAssets = request.inputAssetIds().stream()
                .distinct()
                .map(assetId -> allAssets.stream()
                        .filter(asset -> assetId.equals(asset.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BizException("Input asset does not belong to task: " + assetId)))
                .toList();

        ProcessingJob job = new ProcessingJob();
        job.setTaskId(taskId);
        job.setPipelineId(request.pipelineId());
        job.setExecutorType(ProcessingExecutorType.MANUAL.name());
        job.setStatus(ProcessingJobStatus.SUCCESS.name());
        job.setOperatorName(request.operatorName());
        job.setToolName(request.toolName());
        job.setToolVersion(request.toolVersion());
        job.setParametersJson(writeJson(request.paramsJson()));
        job.setParamsJson(writeJson(request.paramsJson()));
        job.setLogPath(request.logPath());
        job.setRemark(request.remark());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.insert(job);

        List<DataAsset> outputAssets = request.outputAssets().stream()
                .map(outputAsset -> createOutputAsset(taskId, job.getId(), outputAsset))
                .toList();
        for (DataAsset inputAsset : inputAssets) {
            for (DataAsset outputAsset : outputAssets) {
                AssetLineage lineage = new AssetLineage();
                lineage.setTaskId(taskId);
                lineage.setSourceAssetId(inputAsset.getId());
                lineage.setTargetAssetId(outputAsset.getId());
                lineage.setJobId(job.getId());
                lineage.setRelationType(AssetLineageRelationType.JOB_INPUT_OUTPUT.name());
                lineage.setCreatedAt(LocalDateTime.now());
                assetLineageMapper.insert(lineage);
            }
        }

        List<DataAssetResponse> createdAssets = dataAssetService.listAssetResponsesByTaskId(taskId).stream()
                .filter(asset -> outputAssets.stream().anyMatch(created -> created.getId().equals(asset.id())))
                .toList();
        return new ManualProcessingJobResponse(toResponse(job), createdAssets);
    }

    @Override
    public List<ProcessingJobResponse> listJobsByTaskId(Long taskId) {
        acquisitionTaskService.getTask(taskId);
        return processingJobMapper.selectList(
                        new LambdaQueryWrapper<ProcessingJob>()
                                .eq(ProcessingJob::getTaskId, taskId)
                                .orderByDesc(ProcessingJob::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProcessingJobResponse getJob(Long jobId) {
        ProcessingJob job = processingJobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException("Processing job not found: " + jobId);
        }
        return toResponse(job);
    }

    private ProcessingJobResponse toResponse(ProcessingJob job) {
        return new ProcessingJobResponse(
                job.getId(),
                job.getTaskId(),
                job.getPipelineId(),
                job.getExecutorType(),
                job.getStatus(),
                parseJson(job.getParametersJson()),
                parseJson(job.getParamsJson()),
                parseJson(job.getResultJson()),
                job.getErrorMessage(),
                job.getOperatorName(),
                job.getToolName(),
                job.getToolVersion(),
                job.getLogPath(),
                job.getRemark(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    private void validatePipelineId(String pipelineId) {
        if (!PipelineIds.RGB_MOCAP_ALIGNMENT.equals(pipelineId)) {
            throw new BizException("Unsupported pipelineId: " + pipelineId);
        }
    }

    private DataAsset createOutputAsset(Long taskId, Long jobId, CreateDerivedAssetRequest request) {
        return dataAssetService.createDerivedAsset(taskId, jobId, request);
    }

    private String writeJson(JsonNode node) {
        if (node == null) {
            return null;
        }
        return node.toString();
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (IOException exception) {
            throw new BizException("Failed to parse stored processing JSON", exception);
        }
    }
}
