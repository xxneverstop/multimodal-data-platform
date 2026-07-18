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
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import com.honortech.dataplatform.pipeline.mapper.PipelineDefinitionMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class ProcessingJobServiceImpl implements ProcessingJobService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobServiceImpl.class);
    private static final Set<AssetType> REQUIRED_ASSETS = Set.of(AssetType.MOCAP_CSV, AssetType.SMPL_RESULT);

    private final ProcessingJobMapper processingJobMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final DataAssetService dataAssetService;
    private final DataAssetMapper dataAssetMapper;
    private final ProcessingJobExecutor processingJobExecutor;
    private final AssetLineageMapper assetLineageMapper;
    private final CollectionSessionMapper sessionMapper;
    private final DataFileMapper dataFileMapper;
    private final PipelineDefinitionMapper pipelineDefMapper;
    private final StorageRouter storageRouter;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final WorkerPipelineRegistry workerPipelineRegistry;

    public ProcessingJobServiceImpl(
            ProcessingJobMapper processingJobMapper,
            AcquisitionTaskService acquisitionTaskService,
            DataAssetService dataAssetService,
            DataAssetMapper dataAssetMapper,
            ProcessingJobExecutor processingJobExecutor,
            AssetLineageMapper assetLineageMapper,
            CollectionSessionMapper sessionMapper,
            DataFileMapper dataFileMapper,
            PipelineDefinitionMapper pipelineDefMapper,
            StorageRouter storageRouter,
            StorageProperties storageProperties,
            ObjectMapper objectMapper,
            WorkerPipelineRegistry workerPipelineRegistry) {
        this.processingJobMapper = processingJobMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataAssetService = dataAssetService;
        this.dataAssetMapper = dataAssetMapper;
        this.processingJobExecutor = processingJobExecutor;
        this.assetLineageMapper = assetLineageMapper;
        this.sessionMapper = sessionMapper;
        this.dataFileMapper = dataFileMapper;
        this.pipelineDefMapper = pipelineDefMapper;
        this.storageRouter = storageRouter;
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
        this.workerPipelineRegistry = workerPipelineRegistry;
    }

    @Override
    @Transactional
    public ProcessingJobResponse createJob(Long taskId, CreateProcessingJobRequest request) {
        log.info("[处理作业] 创建 task 作业(MOCK): taskId={}, pipelineId={}", taskId, request.pipelineId());
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
        log.info("[处理作业] 创建手动登记作业(MANUAL): taskId={}, pipelineId={}, 输出资产数={}",
                taskId, request.pipelineId(), request.outputAssets().size());
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
        String sessionCode = null;
        if (job.getSessionId() != null) {
            CollectionSession session = sessionMapper.selectById(job.getSessionId());
            sessionCode = session != null ? session.getSessionCode() : null;
        }
        return new ProcessingJobResponse(
                job.getId(),
                job.getTaskId(),
                job.getSessionId(),
                sessionCode,
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
        PipelineDefinition def = pipelineDefMapper.selectOne(
                new LambdaQueryWrapper<PipelineDefinition>()
                        .eq(PipelineDefinition::getPipelineId, pipelineId)
                        .eq(PipelineDefinition::getEnabled, 1));
        if (def == null) {
            // ── 详细诊断：列出 DB 中所有 pipeline，方便比对 ──
            List<PipelineDefinition> all = pipelineDefMapper.selectList(
                    new LambdaQueryWrapper<PipelineDefinition>().select(
                            PipelineDefinition::getPipelineId, PipelineDefinition::getEnabled));
            List<String> allIds = all.stream()
                    .map(p -> p.getPipelineId() + "(enabled=" + p.getEnabled() + ")")
                    .toList();
            log.warn("[处理作业] Pipeline 校验失败: pipelineId='{}' (len={}) DB中不存在或已禁用. "
                     + "DB中全部pipeline({}): {}",
                    pipelineId, pipelineId.length(), all.size(), allIds);
            // 相似匹配提示
            java.util.List<String> closeMatches = new java.util.ArrayList<>();
            for (PipelineDefinition p : all) {
                String existing = p.getPipelineId();
                if (existing.equalsIgnoreCase(pipelineId)) {
                    closeMatches.add("  → 大小写不同: DB=" + existing + " vs 请求=" + pipelineId);
                } else if (existing.trim().equals(pipelineId.trim())) {
                    closeMatches.add("  → 首尾空格差异: DB='" + existing + "' vs 请求='" + pipelineId + "'");
                } else if (existing.toLowerCase().contains(pipelineId.toLowerCase())
                        || pipelineId.toLowerCase().contains(existing.toLowerCase())) {
                    closeMatches.add("  → 部分匹配: DB=" + existing);
                }
            }
            if (!closeMatches.isEmpty()) {
                log.warn("[处理作业] 相似匹配提示: {}", closeMatches);
            }
            throw new BizException("Pipeline not found or disabled: " + pipelineId);
        }
        log.info("[处理作业] Pipeline 校验通过: pipelineId='{}' (len={})", pipelineId, pipelineId.length());
    }

    private DataAsset createOutputAsset(Long taskId, Long jobId, CreateDerivedAssetRequest request) {
        return dataAssetService.createDerivedAsset(taskId, jobId, request);
    }

    @Override
    @Transactional
    public ProcessingJobResponse createSessionJob(Long sessionId, CreateSessionJobRequest request) {
        log.info("[处理作业] 创建 session 作业: sessionId={}, pipelineId={}, executorType=PYTHON_WORKER",
                sessionId, request.pipelineId());
        CollectionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("[处理作业] Session 不存在: {}", sessionId);
            throw new BizException("Session not found: " + sessionId);
        }
        validatePipelineId(request.pipelineId());

        // Worker 注册表校验：Pipeline 在 DB 中存在，还需确认 Worker 端已注册
        if (!workerPipelineRegistry.isRegistered(request.pipelineId())) {
            java.util.Set<String> registered = workerPipelineRegistry.getRegisteredIds();
            log.warn("[处理作业] Pipeline '{}' 在DB中存在但Worker未注册. Worker已注册({}): {}",
                    request.pipelineId(), registered.size(), registered);
            throw new BizException(String.format(
                    "Pipeline '%s' 未在 Worker 端注册。请确认 Worker 已启动并包含该 Pipeline。" +
                    "Worker 当前注册: %s",
                    request.pipelineId(),
                    registered.isEmpty() ? "(无)" : String.join(", ", registered)));
        }

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
        log.info("[处理作业] 作业已创建: jobId={}, pipelineId={}, status=CREATED, 等待 Worker 领取",
                job.getId(), job.getPipelineId());
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
    public List<ProcessingJobResponse> listAllJobs() {
        return processingJobMapper.selectList(
                        new LambdaQueryWrapper<ProcessingJob>()
                                .orderByDesc(ProcessingJob::getCreatedAt)
                                .last("LIMIT 50"))
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
        log.info("[Worker] 作业被领取: jobId={}, pipelineId={}, sessionId={}",
                job.getId(), job.getPipelineId(), job.getSessionId());
        job.setStatus(ProcessingJobStatus.CLAIMED.name());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);

        // 查询 session 下所有 DataFile，按 Pipeline 的 input_asset_types 过滤
        List<DataFile> allFiles = dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>()
                        .eq(DataFile::getSessionId, job.getSessionId()));
        // 获取 Pipeline 声明的输入资产类型，只下发匹配的文件
        PipelineDefinition pipelineDef = pipelineDefMapper.selectOne(
                new LambdaQueryWrapper<PipelineDefinition>()
                        .eq(PipelineDefinition::getPipelineId, job.getPipelineId()));
        List<String> requiredInputTypes = parseInputAssetTypes(
                pipelineDef != null ? pipelineDef.getInputAssetTypes() : null);
        List<DataFile> files;
        if (requiredInputTypes == null || requiredInputTypes.isEmpty()) {
            // 未声明输入类型 → 下发全部文件（向后兼容）
            files = allFiles;
        } else {
            files = allFiles.stream()
                    .filter(f -> f.getAssetType() != null && requiredInputTypes.contains(f.getAssetType()))
                    .toList();
            log.info("[Worker] 作业 {} 按 input_asset_types={} 过滤文件: {}/{} 个匹配",
                    job.getId(), requiredInputTypes, files.size(), allFiles.size());
        }
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
            log.warn("[Worker] 上报成功的作业不存在: jobId={}", jobId);
            throw new BizException("Processing job not found: " + jobId);
        }
        log.info("[Worker] 作业上报成功: jobId={}, pipelineId={}, 产物数={}",
                jobId, job.getPipelineId(), request.outputFiles().size());

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
            log.warn("[Worker] 上报失败的作业不存在: jobId={}", jobId);
            throw new BizException("Processing job not found: " + jobId);
        }
        String errorMsg = request.errorMessage();
        log.error("[Worker] 作业上报失败: jobId={}, pipelineId='{}', status={}, error={}",
                jobId, job.getPipelineId(), job.getStatus(), errorMsg);
        // 如果错误信息是 "Unknown pipeline"，额外打印全部已注册 pipeline 便于排查
        if (errorMsg != null && errorMsg.startsWith("Unknown pipeline:")) {
            List<PipelineDefinition> all = pipelineDefMapper.selectList(
                    new LambdaQueryWrapper<PipelineDefinition>().select(
                            PipelineDefinition::getPipelineId, PipelineDefinition::getEnabled));
            List<String> allIds = all.stream()
                    .map(p -> p.getPipelineId() + "(enabled=" + p.getEnabled() + ")")
                    .toList();
            log.error("[Worker] Unknown pipeline 诊断 — DB中pipeline({}): {}; "
                      + "job创建时间={}, executor={}",
                    all.size(), allIds, job.getCreatedAt(), job.getExecutorType());
        }
        job.setStatus(ProcessingJobStatus.FAILED.name());
        job.setErrorMessage(errorMsg);
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

    private List<String> parseInputAssetTypes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse inputAssetTypes JSON: {}", json);
            return Collections.emptyList();
        }
    }
}
