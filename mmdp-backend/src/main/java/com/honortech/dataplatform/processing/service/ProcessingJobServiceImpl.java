package com.honortech.dataplatform.processing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
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
import com.honortech.dataplatform.processing.util.PipelineIdNormalizer;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
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
        String pipelineId = PipelineIdNormalizer.normalize(request.pipelineId());
        log.info("[处理作业] 创建 task 作业(MOCK): taskId={}, pipelineId={}", taskId, pipelineId);
        acquisitionTaskService.getTask(taskId);
        validatePipelineId(pipelineId);

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
        job.setPipelineId(pipelineId);
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
        String pipelineId = PipelineIdNormalizer.normalize(request.pipelineId());
        log.info("[处理作业] 创建手动登记作业(MANUAL): taskId={}, pipelineId={}, 输出资产数={}",
                taskId, pipelineId, request.outputAssets().size());
        acquisitionTaskService.getTask(taskId);
        validatePipelineId(pipelineId);
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
        job.setPipelineId(pipelineId);
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
        log.info("[处理作业] Pipeline 校验通过: pipelineId='{}'", pipelineId);
    }

    private DataAsset createOutputAsset(Long taskId, Long jobId, CreateDerivedAssetRequest request) {
        return dataAssetService.createDerivedAsset(taskId, jobId, request);
    }

    @Override
    @Transactional
    public ProcessingJobResponse createSessionJob(Long sessionId, CreateSessionJobRequest request) {
        // ── 入口规范化 ──
        String pipelineId = PipelineIdNormalizer.normalize(request.pipelineId());
        log.info("[处理作业] 创建 session 作业: sessionId={}, pipelineId='{}', executorType=PYTHON_WORKER",
                sessionId, pipelineId);

        // ── 1. Session 校验 ──
        CollectionSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            log.warn("[处理作业] Session 不存在: {}", sessionId);
            throw new BizException("Session not found: " + sessionId);
        }

        // ── 2. Pipeline DB 校验 ──
        validatePipelineId(pipelineId);
        log.info("[处理作业] Pipeline DB 校验通过: {}", pipelineId);

        // ── 3. 查询 Pipeline 定义（含 workerType）──
        PipelineDefinition pipelineDef = pipelineDefMapper.selectOne(
                new LambdaQueryWrapper<PipelineDefinition>()
                        .eq(PipelineDefinition::getPipelineId, pipelineId));
        String workerType = (pipelineDef != null && pipelineDef.getWorkerType() != null)
                ? pipelineDef.getWorkerType().strip().toUpperCase() : "CPU";

        // ── 4. Worker 在线校验（按 workerType 检查）──
        if (!workerPipelineRegistry.isWorkerOnline(workerType)) {
            log.warn("[处理作业] {} Worker 离线，拒绝创建: sessionId={}, pipelineId={}, lastRegisteredAt={}",
                    workerType, sessionId, pipelineId, workerPipelineRegistry.getLastRegisteredAt(workerType));
            throw new BizException(String.format(
                    "%s Worker 离线，无法创建处理任务。请确认 %s Worker 已启动。", workerType, workerType));
        }
        log.info("[处理作业] {} Worker 在线校验通过", workerType);

        // ── 5. Worker 注册表校验（按 workerType 检查）──
        if (!workerPipelineRegistry.isRegistered(workerType, pipelineId)) {
            java.util.Set<String> registered = workerPipelineRegistry.getRegisteredIds(workerType);
            log.warn("[处理作业] Pipeline '{}' 在DB中存在但{} Worker未注册. {} Worker已注册({}): {}",
                    pipelineId, workerType, workerType, registered.size(), registered);
            throw new BizException(String.format(
                    "Pipeline '%s' 未在 %s Worker 端注册。请确认 %s Worker 已启动并包含该 Pipeline。" +
                    "%s Worker 当前注册: %s",
                    pipelineId, workerType, workerType,
                    registered.isEmpty() ? "(无)" : String.join(", ", registered)));
        }
        log.info("[处理作业] {} Worker 注册校验通过: {}", workerType, pipelineId);

        // ── 6. 重复提交检查 ──
        List<ProcessingJob> activeJobs = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getSessionId, sessionId)
                        .eq(ProcessingJob::getPipelineId, pipelineId)
                        .in(ProcessingJob::getStatus,
                                ProcessingJobStatus.CREATED.name(),
                                ProcessingJobStatus.CLAIMED.name(),
                                ProcessingJobStatus.RUNNING.name(),
                                ProcessingJobStatus.SUCCESS.name()));
        if (!activeJobs.isEmpty()) {
            ProcessingJob existing = activeJobs.get(0);
            String msg = ProcessingJobStatus.SUCCESS.name().equals(existing.getStatus())
                    ? String.format("该 Pipeline 已成功处理 (Job #%d)，请先清除产物后可重新提交", existing.getId())
                    : String.format("该 Pipeline 已有处理任务进行中 (Job #%d, 状态: %s)，请等待完成",
                            existing.getId(), existing.getStatus());
            log.warn("[处理作业] 重复提交被拒绝: sessionId={}, pipelineId={}, existingJobId={}, existingStatus={}",
                    sessionId, pipelineId, existing.getId(), existing.getStatus());
            throw new BizException(msg);
        }

        // ── 7. 输入文件检查（复用步骤3查出的 pipelineDef）──
        List<String> requiredInputTypes = parseInputAssetTypes(
                pipelineDef != null ? pipelineDef.getInputAssetTypes() : null);
        if (requiredInputTypes != null && !requiredInputTypes.isEmpty()) {
            List<DataFile> sessionFiles = dataFileMapper.selectList(
                    new LambdaQueryWrapper<DataFile>()
                            .eq(DataFile::getSessionId, sessionId));
            boolean hasInput = sessionFiles.stream()
                    .anyMatch(f -> f.getAssetType() != null && requiredInputTypes.contains(f.getAssetType()));
            if (!hasInput) {
                List<String> existingTypes = sessionFiles.stream()
                        .map(DataFile::getAssetType)
                        .filter(t -> t != null)
                        .distinct()
                        .sorted()
                        .toList();
                log.warn("[处理作业] 输入文件不齐: sessionId={}, pipelineId={}, required={}, existing={}",
                        sessionId, pipelineId, requiredInputTypes, existingTypes);
                throw new BizException(String.format(
                        "缺少输入文件，Pipeline '%s' 需要以下类型之一: %s。当前 Session 已有类型: %s",
                        pipelineId,
                        String.join(", ", requiredInputTypes),
                        existingTypes.isEmpty() ? "(无)" : String.join(", ", existingTypes)));
            }
        }

        // ── 8. 创建 Job ──
        ProcessingJob job = new ProcessingJob();
        job.setTaskId(session.getTaskId());
        job.setSessionId(sessionId);
        job.setPipelineId(pipelineId);
        job.setExecutorType(ProcessingExecutorType.PYTHON_WORKER.name());
        job.setStatus(ProcessingJobStatus.CREATED.name());
        job.setParametersJson(writeJson(request.parameters()));
        job.setParamsJson(writeJson(request.parameters()));
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.insert(job);
        log.info("[处理作业] 作业已创建: jobId={}, pipelineId='{}', status=CREATED, sessionId={}, 等待 Worker 领取",
                job.getId(), pipelineId, sessionId);
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
    public WorkerClaimResponse claimJob(String workerType) {
        String wt = (workerType == null || workerType.isBlank()) ? "CPU" : workerType.strip().toUpperCase();
        // 获取所有 CREATED+PYTHON_WORKER 作业，按创建时间升序
        List<ProcessingJob> candidates = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getStatus, ProcessingJobStatus.CREATED.name())
                        .eq(ProcessingJob::getExecutorType, ProcessingExecutorType.PYTHON_WORKER.name())
                        .orderByAsc(ProcessingJob::getCreatedAt));
        if (!candidates.isEmpty()) {
            log.info("[Worker] claimJob(workerType={}) — 当前CREATED+PYTHON_WORKER作业数: {}", wt, candidates.size());
        }
        // 遍历找到第一个属于该 workerType 的 Pipeline Job
        ProcessingJob job = null;
        for (ProcessingJob candidate : candidates) {
            PipelineDefinition def = pipelineDefMapper.selectOne(
                    new LambdaQueryWrapper<PipelineDefinition>()
                            .eq(PipelineDefinition::getPipelineId, candidate.getPipelineId()));
            String jobWorkerType = (def != null && def.getWorkerType() != null)
                    ? def.getWorkerType().strip().toUpperCase() : "CPU";
            if (wt.equals(jobWorkerType)) {
                job = candidate;
                break;
            }
        }
        if (job == null) {
            if (!candidates.isEmpty()) {
                // 有 CREATED 作业但不属于当前 workerType，记录诊断
                List<String> types = candidates.stream()
                        .map(c -> c.getPipelineId() + "(" + c.getStatus() + ")")
                        .toList();
                log.info("[Worker] claimJob(workerType={}): 无匹配的 CREATED 作业，候选作业(不同workerType): {}",
                        wt, types);
            }
            return null;
        }
        log.info("[Worker] 作业被领取(workerType={}): jobId={}, pipelineId='{}', sessionId={}",
                wt, job.getId(), job.getPipelineId(), job.getSessionId());
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
        // 幂等：已 SUCCESS 则直接返回（Worker 重试导致）
        if (ProcessingJobStatus.SUCCESS.name().equals(job.getStatus())) {
            log.info("[Worker] 作业已处于 SUCCESS，幂等跳过: jobId={}, pipelineId={}",
                    jobId, job.getPipelineId());
            return;
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
        // 幂等：已 FAILED 则直接返回（Worker 重试导致）
        if (ProcessingJobStatus.FAILED.name().equals(job.getStatus())) {
            log.info("[Worker] 作业已处于 FAILED，幂等跳过: jobId={}, pipelineId={}",
                    jobId, job.getPipelineId());
            return;
        }
        String errorMsg = request.errorMessage();
        log.error("[Worker] 作业上报失败: jobId={}, pipelineId='{}', error={}",
                jobId, job.getPipelineId(), errorMsg);
        job.setStatus(ProcessingJobStatus.FAILED.name());
        job.setErrorMessage(errorMsg);
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);
    }

    /**
     * CLAIMED 超时回收：每 30 秒扫描 CLAIMED 超过 5 分钟的 PYTHON_WORKER 作业，
     * 回退为 CREATED。防止 Worker 崩溃后 job 永久卡死。
     */
    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void reclaimTimedOutJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<ProcessingJob> stuckJobs = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getStatus, ProcessingJobStatus.CLAIMED.name())
                        .eq(ProcessingJob::getExecutorType, ProcessingExecutorType.PYTHON_WORKER.name())
                        .lt(ProcessingJob::getUpdatedAt, threshold));

        if (stuckJobs.isEmpty()) {
            return;
        }

        for (ProcessingJob job : stuckJobs) {
            long stuckSeconds = java.time.Duration.between(job.getUpdatedAt(), LocalDateTime.now()).getSeconds();
            log.warn("[超时回收] jobId={}, pipelineId='{}', 已 CLAIMED {} 秒，回退为 CREATED",
                    job.getId(), job.getPipelineId(), stuckSeconds);
            job.setStatus(ProcessingJobStatus.CREATED.name());
            job.setUpdatedAt(LocalDateTime.now());
            processingJobMapper.updateById(job);
        }
        log.info("[超时回收] 已回收 {} 个超时 Job", stuckJobs.size());
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
