package com.honortech.dataplatform.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.pipeline.dto.CreatePipelineRequest;
import com.honortech.dataplatform.pipeline.dto.PipelineDefinitionResponse;
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import com.honortech.dataplatform.pipeline.entity.ProfilePipeline;
import com.honortech.dataplatform.pipeline.mapper.PipelineDefinitionMapper;
import com.honortech.dataplatform.pipeline.mapper.ProfilePipelineMapper;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PipelineDefinitionServiceImpl implements PipelineDefinitionService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final PipelineDefinitionMapper pipelineMapper;
    private final ProfilePipelineMapper profilePipelineMapper;
    private final CollectionSessionMapper sessionMapper;
    private final DataAssetService dataAssetService;
    private final ObjectMapper objectMapper;

    public PipelineDefinitionServiceImpl(
            PipelineDefinitionMapper pipelineMapper,
            ProfilePipelineMapper profilePipelineMapper,
            CollectionSessionMapper sessionMapper,
            DataAssetService dataAssetService,
            ObjectMapper objectMapper) {
        this.pipelineMapper = pipelineMapper;
        this.profilePipelineMapper = profilePipelineMapper;
        this.sessionMapper = sessionMapper;
        this.dataAssetService = dataAssetService;
        this.objectMapper = objectMapper;
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
    public List<PipelineDefinitionResponse> getAvailablePipelines(Long sessionId) {
        CollectionSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getProfileId() == null) {
            return Collections.emptyList();
        }

        // 获取该 Profile 关联的所有 Pipeline
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

        // 获取 session 已有资产类型
        List<DataAsset> assets = dataAssetService.listByTaskId(session.getTaskId());
        List<String> existingAssetTypes = assets.stream()
                .map(DataAsset::getAssetType)
                .filter(type -> type != null)
                .distinct()
                .toList();

        // 过滤：session 有足够输入资产的 pipeline 才返回
        return pipelines.stream()
                .filter(p -> hasRequiredInputs(p, existingAssetTypes))
                .map(this::toResponse)
                .toList();
    }

    private boolean hasRequiredInputs(PipelineDefinition pipeline, List<String> existingAssetTypes) {
        List<String> required = parseStringList(pipeline.getInputAssetTypes());
        if (required == null || required.isEmpty()) {
            return true;
        }
        for (String requiredType : required) {
            if (!existingAssetTypes.contains(requiredType)) {
                return false;
            }
        }
        return true;
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
                pipeline.getUpdatedAt()
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
