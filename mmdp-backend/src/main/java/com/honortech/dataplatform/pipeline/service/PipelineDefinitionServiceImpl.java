package com.honortech.dataplatform.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.ProcessingJobStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.pipeline.dto.CreatePipelineRequest;
import com.honortech.dataplatform.pipeline.dto.PipelineDefinitionResponse;
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import com.honortech.dataplatform.pipeline.entity.ProfilePipeline;
import com.honortech.dataplatform.pipeline.mapper.PipelineDefinitionMapper;
import com.honortech.dataplatform.pipeline.mapper.ProfilePipelineMapper;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.processing.util.PipelineIdNormalizer;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PipelineDefinitionServiceImpl implements PipelineDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineDefinitionServiceImpl.class);

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final PipelineDefinitionMapper pipelineMapper;
    private final ProfilePipelineMapper profilePipelineMapper;
    private final CollectionSessionMapper sessionMapper;
    private final DataAssetService dataAssetService;
    private final ProcessingJobMapper processingJobMapper;
    private final ObjectMapper objectMapper;
    private final com.honortech.dataplatform.processing.service.WorkerPipelineRegistry workerPipelineRegistry;

    public PipelineDefinitionServiceImpl(
            PipelineDefinitionMapper pipelineMapper,
            ProfilePipelineMapper profilePipelineMapper,
            CollectionSessionMapper sessionMapper,
            DataAssetService dataAssetService,
            ProcessingJobMapper processingJobMapper,
            ObjectMapper objectMapper,
            com.honortech.dataplatform.processing.service.WorkerPipelineRegistry workerPipelineRegistry) {
        this.pipelineMapper = pipelineMapper;
        this.profilePipelineMapper = profilePipelineMapper;
        this.sessionMapper = sessionMapper;
        this.dataAssetService = dataAssetService;
        this.processingJobMapper = processingJobMapper;
        this.objectMapper = objectMapper;
        this.workerPipelineRegistry = workerPipelineRegistry;
    }

    @Override
    public List<PipelineDefinitionResponse> listPipelines() {
        List<PipelineDefinition> pipelines = pipelineMapper.selectList(
                new LambdaQueryWrapper<PipelineDefinition>().orderByAsc(PipelineDefinition::getId));
        return pipelines.stream().map(this::toResponse).toList();
    }

    @Override
    public PipelineDefinitionResponse getPipeline(Long id) {
        PipelineDefinition pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new BizException("Pipeline not found: " + id);
        }
        return toResponse(pipeline);
    }

    @Override
    @Transactional
    public PipelineDefinitionResponse createPipeline(CreatePipelineRequest request) {
        PipelineDefinition existing = pipelineMapper.selectOne(
                new LambdaQueryWrapper<PipelineDefinition>()
                        .eq(PipelineDefinition::getPipelineId, request.pipelineId()));
        if (existing != null) {
            throw new BizException("Pipeline already exists: " + request.pipelineId());
        }

        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setPipelineId(request.pipelineId());
        pipeline.setDisplayName(request.displayName());
        pipeline.setDescription(request.description());
        pipeline.setInputAssetTypes(toJson(request.inputAssetTypes()));
        pipeline.setOutputAssetTypes(toJson(request.outputAssetTypes()));
        pipeline.setExecutorType(request.executorType());
        pipeline.setEnabled(1);
        pipeline.setCreatedAt(LocalDateTime.now());
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.insert(pipeline);

        syncProfileLinks(pipeline.getPipelineId(), request.profileIds());
        return toResponse(pipeline);
    }

    @Override
    @Transactional
    public PipelineDefinitionResponse updatePipeline(Long id, CreatePipelineRequest request) {
        PipelineDefinition pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new BizException("Pipeline not found: " + id);
        }
        pipeline.setDisplayName(request.displayName());
        pipeline.setDescription(request.description());
        pipeline.setInputAssetTypes(toJson(request.inputAssetTypes()));
        pipeline.setOutputAssetTypes(toJson(request.outputAssetTypes()));
        pipeline.setExecutorType(request.executorType());
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);

        syncProfileLinks(pipeline.getPipelineId(), request.profileIds());
        return toResponse(pipeline);
    }

    @Override
    public void disablePipeline(Long id) {
        PipelineDefinition pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new BizException("Pipeline not found: " + id);
        }
        pipeline.setEnabled(0);
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
    }

    @Override
    public void enablePipeline(Long id) {
        PipelineDefinition pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new BizException("Pipeline not found: " + id);
        }
        pipeline.setEnabled(1);
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
    }

    /** 不可重复提交的 job 状态 */
    private static final Set<String> ACTIVE_JOB_STATUSES = Set.of(
            ProcessingJobStatus.CREATED.name(),
            ProcessingJobStatus.CLAIMED.name(),
            ProcessingJobStatus.RUNNING.name(),
            ProcessingJobStatus.SUCCESS.name());

    @Override
    public List<PipelineDefinitionResponse> getAvailablePipelines(Long sessionId) {
        CollectionSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getProfileId() == null) {
            return Collections.emptyList();
        }

        // 1. 获取该 Profile 关联的所有 Pipeline
        List<ProfilePipeline> links = profilePipelineMapper.selectList(
                new LambdaQueryWrapper<ProfilePipeline>()
                        .eq(ProfilePipeline::getProfileId, session.getProfileId())
                        .eq(ProfilePipeline::getEnabled, 1));
        if (links.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> pipelineIds = links.stream().map(ProfilePipeline::getPipelineId).distinct().toList();
        List<PipelineDefinition> pipelines = pipelineMapper.selectList(
                new LambdaQueryWrapper<PipelineDefinition>()
                        .in(PipelineDefinition::getPipelineId, pipelineIds)
                        .eq(PipelineDefinition::getEnabled, 1));

        // 2. 获取 session 现有资产类型（用于输入文件校验）
        List<DataAsset> assets = dataAssetService.listByTaskId(session.getTaskId());
        List<String> existingAssetTypes = assets.stream()
                .map(DataAsset::getAssetType)
                .filter(type -> type != null)
                .distinct()
                .toList();

        // 3. 获取该 session 下所有 job，按 pipelineId 分组取最新
        List<ProcessingJob> sessionJobs = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>()
                        .eq(ProcessingJob::getSessionId, sessionId));
        Map<String, ProcessingJob> latestJobByPipeline = sessionJobs.stream()
                .collect(Collectors.groupingBy(
                        ProcessingJob::getPipelineId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(ProcessingJob::getCreatedAt)),
                                opt -> opt.orElse(null))));

        // 4. 遍历每个 pipeline，计算可用性
        List<PipelineDefinitionResponse> result = new ArrayList<>();
        for (PipelineDefinition pipeline : pipelines) {
            String pid = pipeline.getPipelineId();
            ProcessingJob latestJob = latestJobByPipeline.get(pid);
            String latestJobStatus = latestJob != null ? latestJob.getStatus() : null;

            boolean ready = true;
            String blockedReason = null;

            // 检查 1: Worker 注册
            if (!workerPipelineRegistry.isRegistered(PipelineIdNormalizer.normalize(pid))) {
                ready = false;
                blockedReason = "Pipeline 未在 Worker 端注册，请确认 Worker 已启动并包含该 Pipeline";
            }
            // 检查 2: 输入文件（OR 逻辑：至少一种匹配）
            else if (!hasRequiredInputs(pipeline, existingAssetTypes)) {
                ready = false;
                List<String> required = parseStringList(pipeline.getInputAssetTypes());
                if (required == null || required.isEmpty()) {
                    blockedReason = "该 Pipeline 未声明输入资产类型，无法判断输入文件是否齐全";
                } else {
                    blockedReason = "缺少输入文件，需要以下类型之一: " + String.join(", ", required);
                }
            }
            // 检查 3: 是否存在活跃 job 阻止重新提交
            else if (latestJob != null && ACTIVE_JOB_STATUSES.contains(latestJobStatus)) {
                ready = false;
                if (ProcessingJobStatus.SUCCESS.name().equals(latestJobStatus)) {
                    blockedReason = "已成功完成处理 (Job #" + latestJob.getId()
                            + ")，请先清除产物后可重新提交";
                } else {
                    blockedReason = "处理任务进行中 (Job #" + latestJob.getId()
                            + ", 状态: " + latestJobStatus + ")，请等待完成";
                }
            }

            result.add(new PipelineDefinitionResponse(
                    pipeline.getId(),
                    pipeline.getPipelineId(),
                    pipeline.getDisplayName(),
                    pipeline.getDescription(),
                    parseStringList(pipeline.getInputAssetTypes()),
                    parseStringList(pipeline.getOutputAssetTypes()),
                    pipeline.getExecutorType(),
                    pipeline.getEnabled(),
                    Collections.emptyList(), // session 级查询不需要 profileIds
                    pipeline.getCreatedAt(),
                    pipeline.getUpdatedAt(),
                    latestJobStatus,
                    ready,
                    blockedReason));
        }
        return result;
    }

    /**
     * 判断 session 现有资产是否能满足 pipeline 的输入要求。
     * 使用 OR 逻辑：只要 session 拥有任意一种 pipeline 声明的输入类型，即视为可用。
     * inputAssetTypes 为空时表示不限输入，始终可用。
     */
    private boolean hasRequiredInputs(PipelineDefinition pipeline, List<String> existingAssetTypes) {
        List<String> required = parseStringList(pipeline.getInputAssetTypes());
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String requiredType : required) {
            if (existingAssetTypes.contains(requiredType)) {
                return true;
            }
        }
        return false;
    }

    private void syncProfileLinks(String pipelineId, List<Long> profileIds) {
        // 清除旧的
        List<ProfilePipeline> existing = profilePipelineMapper.selectList(
                new LambdaQueryWrapper<ProfilePipeline>()
                        .eq(ProfilePipeline::getPipelineId, pipelineId));
        for (ProfilePipeline link : existing) {
            profilePipelineMapper.deleteById(link.getId());
        }
        // 插入新的
        if (profileIds != null) {
            for (Long profileId : profileIds) {
                ProfilePipeline link = new ProfilePipeline();
                link.setProfileId(profileId);
                link.setPipelineId(pipelineId);
                link.setEnabled(1);
                link.setCreatedAt(LocalDateTime.now());
                profilePipelineMapper.insert(link);
            }
        }
    }

    private PipelineDefinitionResponse toResponse(PipelineDefinition pipeline) {
        List<ProfilePipeline> links = profilePipelineMapper.selectList(
                new LambdaQueryWrapper<ProfilePipeline>()
                        .eq(ProfilePipeline::getPipelineId, pipeline.getPipelineId())
                        .eq(ProfilePipeline::getEnabled, 1));
        List<Long> profileIds = links.stream().map(ProfilePipeline::getProfileId).toList();

        return new PipelineDefinitionResponse(
                pipeline.getId(),
                pipeline.getPipelineId(),
                pipeline.getDisplayName(),
                pipeline.getDescription(),
                parseStringList(pipeline.getInputAssetTypes()),
                parseStringList(pipeline.getOutputAssetTypes()),
                pipeline.getExecutorType(),
                pipeline.getEnabled(),
                profileIds,
                pipeline.getCreatedAt(),
                pipeline.getUpdatedAt(),
                null,   // latestJobStatus: 全局查询无 session 上下文
                false,  // isReady: 全局查询不可用
                null    // blockedReason: 全局查询不可用
        );
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BizException("Failed to serialize asset types");
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
