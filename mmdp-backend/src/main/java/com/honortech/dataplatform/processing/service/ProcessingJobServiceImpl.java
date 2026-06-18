package com.honortech.dataplatform.processing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.common.enums.AssetLineageRelationType;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.ProcessingExecutorType;
import com.honortech.dataplatform.common.enums.ProcessingJobStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetLineageRelationType;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.FileUploadStatus;
import com.honortech.dataplatform.common.enums.ProcessingExecutorType;
import com.honortech.dataplatform.common.enums.ProcessingJobStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.StorageProperties;
import com.honortech.dataplatform.common.storage.StorageProvider;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.common.util.FileNameUtils;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.processing.dto.CreateManualProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateSessionJobRequest;
import com.honortech.dataplatform.processing.dto.ManualProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.WorkerClaimResponse;
import com.honortech.dataplatform.processing.dto.WorkerFailureRequest;
import com.honortech.dataplatform.processing.dto.WorkerSuccessRequest;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
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
    private final DataAssetMapper dataAssetMapper;
    private final ProcessingJobExecutor processingJobExecutor;
    private final AssetLineageMapper assetLineageMapper;
    private final CollectionSessionMapper sessionMapper;
    private final DataFileMapper dataFileMapper;
    private final StorageRouter storageRouter;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    public ProcessingJobServiceImpl(
            ProcessingJobMapper processingJobMapper,
            AcquisitionTaskService acquisitionTaskService,
            DataAssetService dataAssetService,
            DataAssetMapper dataAssetMapper,
            ProcessingJobExecutor processingJobExecutor,
            AssetLineageMapper assetLineageMapper,
            CollectionSessionMapper sessionMapper,
            DataFileMapper dataFileMapper,
            StorageRouter storageRouter,
            StorageProperties storageProperties,
            ObjectMapper objectMapper) {
        this.processingJobMapper = processingJobMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataAssetService = dataAssetService;
        this.dataAssetMapper = dataAssetMapper;
        this.processingJobExecutor = processingJobExecutor;
        this.assetLineageMapper = assetLineageMapper;
        this.sessionMapper = sessionMapper;
        this.dataFileMapper = dataFileMapper;
        this.storageRouter = storageRouter;
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ProcessingJobResponse createJob(Long taskId, CreateProcessingJobRequest request) {
        acquisitionTaskService.getTask(taskId);
        validatePipelineId(request.pipelineId());

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
                job.getSessionId(),
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
        if (!PipelineIds.RGB_MOCAP_ALIGNMENT.equals(pipelineId)
                && !PipelineIds.BUILD_PLAYBACK.equals(pipelineId)) {
            throw new BizException("Unsupported pipelineId: " + pipelineId);
        }
    }

    private DataAsset createOutputAsset(Long taskId, Long jobId, CreateDerivedAssetRequest request) {
        return dataAssetService.createDerivedAsset(taskId, jobId, request);
    }

    @Override
    @Transactional
    public ProcessingJobResponse createSessionJob(Long sessionId, CreateSessionJobRequest request) {
        CollectionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("Session not found: " + sessionId);
        }
        validatePipelineId(request.pipelineId());

        ProcessingJob job = new ProcessingJob();
        job.setTaskId(session.getTaskId());
        job.setSessionId(sessionId);
        job.setPipelineId(request.pipelineId());
        job.setExecutorType(ProcessingExecutorType.PYTHON_WORKER.name());
        job.setStatus(ProcessingJobStatus.CREATED.name());
        job.setParametersJson(writeJson(request.parameters()));
        job.setParamsJson(writeJson(request.parameters()));
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.insert(job);
        return toResponse(job);
    }

    @Override
    public List<ProcessingJobResponse> listJobsBySessionId(Long sessionId) {
        return processingJobMapper.selectList(
                        new LambdaQueryWrapper<ProcessingJob>()
                                .eq(ProcessingJob::getSessionId, sessionId)
                                .orderByDesc(ProcessingJob::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public WorkerClaimResponse claimJob() {
        ProcessingJob job = processingJobMapper.selectOne(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getStatus, ProcessingJobStatus.CREATED.name())
                        .eq(ProcessingJob::getExecutorType, ProcessingExecutorType.PYTHON_WORKER.name())
                        .orderByAsc(ProcessingJob::getCreatedAt)
                        .last("LIMIT 1"));
        if (job == null) {
            return null;
        }
        job.setStatus(ProcessingJobStatus.CLAIMED.name());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);

        // 查询 session 下所有 DataFile，构建 inputFiles
        List<DataFile> files = dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>()
                        .eq(DataFile::getSessionId, job.getSessionId()));
        String ossEndpoint = storageProperties.getOss().getEndpoint();
        List<WorkerClaimResponse.WorkerInputFile> inputFiles = files.stream()
                .map(f -> new WorkerClaimResponse.WorkerInputFile(
                        f.getSourceKey(),
                        f.getAssetType(),
                        f.getOriginalFilename(),
                        f.getObjectKey(),
                        f.getBucketName(),
                        ossEndpoint,
                        f.getFileSize()))
                .toList();

        return new WorkerClaimResponse(
                job.getId(), job.getTaskId(), job.getSessionId(),
                job.getPipelineId(),
                parseJson(job.getParametersJson()),
                inputFiles,
                job.getCreatedAt());
    }

    @Override
    @Transactional
    public void completeJob(Long jobId, WorkerSuccessRequest request) {
        ProcessingJob job = processingJobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException("Processing job not found: " + jobId);
        }

        // 获取 session 已有的输入资产（用于血缘追踪）
        List<DataAsset> inputAssets = dataAssetService.listByTaskId(job.getTaskId());

        for (WorkerSuccessRequest.OutputFile output : request.outputFiles()) {
            // 创建 DataFile
            DataFile dataFile = new DataFile();
            dataFile.setTaskId(job.getTaskId());
            dataFile.setSessionId(job.getSessionId());
            dataFile.setFileRole("PROCESSED_OUTPUT");
            dataFile.setSourceKey(output.sourceKey());
            dataFile.setOriginalFilename(output.fileName());
            dataFile.setFileExt(FileNameUtils.getExtension(output.fileName()));
            dataFile.setContentType(output.contentType() != null ? output.contentType() : "application/octet-stream");
            dataFile.setFileSize(output.fileSize() != null ? output.fileSize() : 0L);
            dataFile.setObjectKey(output.objectKey());
            String bucket = storageProperties.getOss().getBucket();
            dataFile.setBucketName(bucket);
            dataFile.setStorageUrl("oss://" + bucket + "/" + output.objectKey());
            StorageProvider storageProvider = storageRouter.defaultService().provider();
            dataFile.setStorageProvider((storageProvider == null ? StorageProvider.OSS : storageProvider).name());
            dataFile.setUploadStatus(FileUploadStatus.SUCCESS.name());
            dataFile.setAssetType(output.assetType());
            dataFile.setCreatedAt(LocalDateTime.now());
            dataFileMapper.insert(dataFile);

            // 创建输出资产
            DataAsset outputAsset = dataAssetService.createAcquisitionAsset(
                    job.getTaskId(), job.getSessionId(), output.sourceKey(), dataFile,
                    AssetType.fromNullable(output.assetType()));
            // 标记产物来源 job，前端据此区分原始资产 vs 处理产物
            outputAsset.setProducedByJobId(job.getId());
            dataAssetMapper.updateById(outputAsset);

            // 创建血缘：所有输入资产 → 此输出资产
            for (DataAsset input : inputAssets) {
                AssetLineage lineage = new AssetLineage();
                lineage.setTaskId(job.getTaskId());
                lineage.setSessionId(job.getSessionId());
                lineage.setSourceAssetId(input.getId());
                lineage.setTargetAssetId(outputAsset.getId());
                lineage.setJobId(job.getId());
                lineage.setRelationType(AssetLineageRelationType.JOB_INPUT_OUTPUT.name());
                lineage.setCreatedAt(LocalDateTime.now());
                assetLineageMapper.insert(lineage);
            }
        }

        job.setStatus(ProcessingJobStatus.SUCCESS.name());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);
    }

    @Override
    @Transactional
    public void failJob(Long jobId, WorkerFailureRequest request) {
        ProcessingJob job = processingJobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException("Processing job not found: " + jobId);
        }
        job.setStatus(ProcessingJobStatus.FAILED.name());
        job.setErrorMessage(request.errorMessage());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);
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
