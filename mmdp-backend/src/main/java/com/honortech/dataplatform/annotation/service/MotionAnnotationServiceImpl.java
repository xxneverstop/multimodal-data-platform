package com.honortech.dataplatform.annotation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.annotation.dto.AnnotationProgressResponse;
import com.honortech.dataplatform.annotation.dto.FrameIssueItem;
import com.honortech.dataplatform.annotation.dto.MotionAnnotationRequest;
import com.honortech.dataplatform.annotation.dto.MotionAnnotationResponse;
import com.honortech.dataplatform.annotation.entity.MotionAnnotation;
import com.honortech.dataplatform.annotation.mapper.MotionAnnotationMapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.common.enums.AnnotationStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.user.entity.SysUser;
import com.honortech.dataplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MotionAnnotationServiceImpl implements MotionAnnotationService {

    private final MotionAnnotationMapper motionAnnotationMapper;
    private final DataAssetMapper dataAssetMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public MotionAnnotationServiceImpl(
            MotionAnnotationMapper motionAnnotationMapper,
            DataAssetMapper dataAssetMapper,
            SysUserMapper sysUserMapper,
            ObjectMapper objectMapper) {
        this.motionAnnotationMapper = motionAnnotationMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    private DataAsset requireAsset(Long assetId) {
        DataAsset asset = dataAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new BizException("数据资产不存在: " + assetId);
        }
        return asset;
    }

    private MotionAnnotation queryByAssetId(Long assetId) {
        return motionAnnotationMapper.selectOne(
                new LambdaQueryWrapper<MotionAnnotation>()
                        .eq(MotionAnnotation::getAssetId, assetId)
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new BizException("JSON序列化失败: " + e.getMessage());
        }
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private List<FrameIssueItem> parseFrameIssues(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FrameIssueItem>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private Map<String, String> parseDefects(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private List<String> parseTextDescriptions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String resolveAnnotatorName(Long annotatorId) {
        if (annotatorId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(annotatorId);
        return user != null ? user.getDisplayName() : null;
    }

    private MotionAnnotationResponse toResponse(MotionAnnotation annotation) {
        return new MotionAnnotationResponse(
                annotation.getId(),
                annotation.getAssetId(),
                annotation.getStatus(),
                annotation.getQualityRating(),
                parseTags(annotation.getMotionTags()),
                parseDefects(annotation.getMotiondbDefects()),
                parseFrameIssues(annotation.getFrameIssues()),
                parseTextDescriptions(annotation.getTextDescriptions()),
                annotation.getOverallComment(),
                annotation.getAnnotatorId(),
                resolveAnnotatorName(annotation.getAnnotatorId()),
                annotation.getVersion(),
                annotation.getCreatedAt(),
                annotation.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public MotionAnnotationResponse getByAssetId(Long assetId) {
        requireAsset(assetId);
        MotionAnnotation annotation = queryByAssetId(assetId);
        if (annotation == null) {
            annotation = new MotionAnnotation();
            annotation.setAssetId(assetId);
            annotation.setStatus(AnnotationStatus.UNANNOTATED.name());
            annotation.setVersion(0);
            annotation.setCreatedAt(LocalDateTime.now());
            annotation.setUpdatedAt(LocalDateTime.now());
            motionAnnotationMapper.insert(annotation);
        }
        return toResponse(annotation);
    }

    @Override
    public MotionAnnotationResponse getByFileId(Long fileId) {
        DataAsset asset = dataAssetMapper.selectOne(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getFileId, fileId)
                        .last("limit 1")
        );
        if (asset == null) {
            throw new BizException("未找到 fileId=" + fileId + " 对应的数据资产");
        }
        return getByAssetId(asset.getId());
    }

    @Override
    @Transactional
    public MotionAnnotationResponse upsert(Long assetId, MotionAnnotationRequest request, Long annotatorId) {
        requireAsset(assetId);
        MotionAnnotation annotation = queryByAssetId(assetId);

        if (annotation != null) {
            // 乐观锁校验
            Integer expectedVersion = request.version();
            if (expectedVersion != null && !expectedVersion.equals(annotation.getVersion())) {
                throw new BizException("标注已被他人修改，请刷新后重试");
            }
        } else {
            annotation = new MotionAnnotation();
            annotation.setAssetId(assetId);
            annotation.setStatus(AnnotationStatus.UNANNOTATED.name());
            annotation.setVersion(0);
            annotation.setCreatedAt(LocalDateTime.now());
        }

        if (request.status() != null) {
            annotation.setStatus(request.status());
        }
        annotation.setQualityRating(request.qualityRating());
        annotation.setMotionTags(request.motionTags() != null ? toJson(request.motionTags()) : null);
        annotation.setMotiondbDefects(request.motiondbDefects() != null ? toJson(request.motiondbDefects()) : null);
        annotation.setFrameIssues(request.frameIssues() != null ? toJson(request.frameIssues()) : null);
        annotation.setTextDescriptions(request.textDescriptions() != null ? toJson(request.textDescriptions()) : null);
        annotation.setOverallComment(request.overallComment());
        annotation.setAnnotatorId(annotatorId);
        annotation.setUpdatedAt(LocalDateTime.now());

        if (annotation.getId() == null) {
            motionAnnotationMapper.insert(annotation);
        } else {
            int newVersion = annotation.getVersion() + 1;
            int affected = motionAnnotationMapper.update(null,
                    new LambdaUpdateWrapper<MotionAnnotation>()
                            .eq(MotionAnnotation::getId, annotation.getId())
                            .eq(MotionAnnotation::getVersion, annotation.getVersion())
                            .set(MotionAnnotation::getStatus, annotation.getStatus())
                            .set(MotionAnnotation::getQualityRating, annotation.getQualityRating())
                            .set(MotionAnnotation::getMotionTags, annotation.getMotionTags())
                            .set(MotionAnnotation::getMotiondbDefects, annotation.getMotiondbDefects())
                            .set(MotionAnnotation::getFrameIssues, annotation.getFrameIssues())
                            .set(MotionAnnotation::getTextDescriptions, annotation.getTextDescriptions())
                            .set(MotionAnnotation::getOverallComment, annotation.getOverallComment())
                            .set(MotionAnnotation::getAnnotatorId, annotation.getAnnotatorId())
                            .set(MotionAnnotation::getVersion, newVersion)
                            .set(MotionAnnotation::getUpdatedAt, annotation.getUpdatedAt())
            );
            if (affected == 0) {
                throw new BizException("标注已被他人修改，请刷新后重试");
            }
            annotation.setVersion(newVersion);
        }

        return toResponse(annotation);
    }

    @Override
    public AnnotationProgressResponse getSessionProgress(Long sessionId) {
        List<DataAsset> assets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getSessionId, sessionId)
        );
        return buildProgress(assets);
    }

    @Override
    public AnnotationProgressResponse getTaskProgress(Long taskId) {
        List<DataAsset> assets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getTaskId, taskId)
        );
        return buildProgress(assets);
    }

    private AnnotationProgressResponse buildProgress(List<DataAsset> assets) {
        if (assets.isEmpty()) {
            return new AnnotationProgressResponse(0, 0, 0, 0, Map.of());
        }

        List<Long> assetIds = assets.stream().map(DataAsset::getId).toList();
        List<MotionAnnotation> annotations = motionAnnotationMapper.selectList(
                new LambdaQueryWrapper<MotionAnnotation>()
                        .in(MotionAnnotation::getAssetId, assetIds)
        );

        Map<Long, MotionAnnotation> annotationMap = new HashMap<>();
        for (MotionAnnotation a : annotations) {
            annotationMap.put(a.getAssetId(), a);
        }

        int annotatedCount = 0;
        int inProgressCount = 0;
        int unannotatedCount = 0;
        Map<String, Integer> ratingDistribution = new HashMap<>();

        for (DataAsset asset : assets) {
            MotionAnnotation annotation = annotationMap.get(asset.getId());
            if (annotation == null || AnnotationStatus.UNANNOTATED.name().equals(annotation.getStatus())) {
                unannotatedCount++;
            } else if (AnnotationStatus.ANNOTATED.name().equals(annotation.getStatus())) {
                annotatedCount++;
                String rating = annotation.getQualityRating();
                if (rating != null && !rating.isBlank()) {
                    ratingDistribution.merge(rating, 1, Integer::sum);
                }
            } else {
                inProgressCount++;
            }
        }

        return new AnnotationProgressResponse(
                assets.size(),
                annotatedCount,
                inProgressCount,
                unannotatedCount,
                ratingDistribution
        );
    }
}
