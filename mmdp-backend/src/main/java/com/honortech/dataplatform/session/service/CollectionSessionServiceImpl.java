package com.honortech.dataplatform.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.profile.rule.PlaybackRuleResolver;
import com.honortech.dataplatform.profile.rule.ProfileRuleRegistry;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import com.honortech.dataplatform.session.dto.SessionListItemResponse;
import com.honortech.dataplatform.session.dto.SessionListQueryRequest;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import com.honortech.dataplatform.session.dto.SessionResponse;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CollectionSessionServiceImpl implements CollectionSessionService {

    private final CollectionSessionMapper sessionMapper;
    private final DataFileMapper dataFileMapper;
    private final DataAssetMapper dataAssetMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final ObjectMapper objectMapper;
    private final CollectionProfileService collectionProfileService;
    private final ProfileRuleRegistry profileRuleRegistry;

    public CollectionSessionServiceImpl(
            CollectionSessionMapper sessionMapper,
            DataFileMapper dataFileMapper,
            DataAssetMapper dataAssetMapper,
            AcquisitionTaskService acquisitionTaskService,
            ObjectMapper objectMapper,
            CollectionProfileService collectionProfileService,
            ProfileRuleRegistry profileRuleRegistry) {
        this.sessionMapper = sessionMapper;
        this.dataFileMapper = dataFileMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.objectMapper = objectMapper;
        this.collectionProfileService = collectionProfileService;
        this.profileRuleRegistry = profileRuleRegistry;
    }

    @Override
    public List<SessionResponse> listByTaskId(Long taskId) {
        String taskName = acquisitionTaskService.getTask(taskId).getTaskName();
        List<CollectionSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .eq(CollectionSession::getTaskId, taskId)
                        .orderByDesc(CollectionSession::getCreatedAt)
        );
        return sessions.stream().map(session -> toResponse(session, taskName)).toList();
    }

    @Override
    public List<SessionResponse> listAll() {
        List<CollectionSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>().orderByDesc(CollectionSession::getCreatedAt)
        );
        List<SessionResponse> result = new ArrayList<>();
        for (CollectionSession session : sessions) {
            String taskName;
            try {
                taskName = acquisitionTaskService.getTask(session.getTaskId()).getTaskName();
            } catch (Exception exception) {
                taskName = "Task-" + session.getTaskId();
            }
            result.add(toResponse(session, taskName));
        }
        return result;
    }

    @Override
    public IPage<SessionListItemResponse> listPage(SessionListQueryRequest request) {
        Page<SessionListItemResponse> page = new Page<>(
                request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage(),
                request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize()
        );
        return sessionMapper.selectSessionListPage(page, request, resolveOrderByClause(request.getSortBy(), request.getSortOrder()));
    }

    @Override
    public CollectionSession getBySessionId(String sessionId) {
        CollectionSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<CollectionSession>().eq(CollectionSession::getSessionId, sessionId)
        );
        if (session == null) {
            throw new BizException("Session not found: " + sessionId);
        }
        return session;
    }

    @Override
    public SessionPlaybackResponse getPlaybackData(String sessionId) {
        CollectionSession session = getBySessionId(sessionId);
        JsonNode manifest = parseJson(session.getManifestJson());
        List<DataFile> sessionFiles = findSessionFiles(session.getTaskId(), session.getId(), sessionId);
        Map<String, SessionPlaybackResponse.PlaybackSource> sources = Map.of();
        String profileCode = null;
        if (session.getProfileId() != null) {
            CollectionProfile profile = collectionProfileService.getRequiredById(session.getProfileId());
            List<CollectionProfileSource> profileSources = collectionProfileService.listSourcesByProfileId(profile.getId());
            PlaybackRuleResolver playbackRule = profileRuleRegistry.getPlaybackRule(profile.getPlaybackRuleCode());
            sources = playbackRule.buildSources(profile, profileSources, sessionFiles, manifest);
            profileCode = profile.getProfileCode();
        }
        return new SessionPlaybackResponse(
                session.getSessionId(),
                session.getSessionCode(),
                session.getTaskId(),
                session.getSubjectCode(),
                session.getActionName(),
                profileCode,
                session.getTimestampPolicy(),
                session.getStartedAt(),
                session.getDurationMs(),
                sources
        );
    }

    private SessionResponse toResponse(CollectionSession session, String taskName) {
        CollectionProfile profile = session.getProfileId() == null ? null : collectionProfileService.getRequiredById(session.getProfileId());
        return new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getTaskId(),
                taskName,
                session.getSessionId(),
                session.getSessionId(),
                session.getSubjectCode(),
                session.getActionName(),
                session.getProfileId(),
                profile == null ? null : profile.getProfileCode(),
                profile == null ? null : profile.getProfileName(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationMs(),
                session.getUploadStatus(),
                session.getSessionStatus(),
                session.getCreatedAt(),
                findSessionAssets(session.getTaskId(), session.getId(), session.getSessionId())
        );
    }

    private List<DataFile> findSessionFiles(Long taskId, Long sessionRecordId, String sessionId) {
        List<DataFile> allFiles = dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>()
                        .eq(DataFile::getTaskId, taskId)
                        .orderByAsc(DataFile::getCreatedAt)
        );
        List<DataFile> result = new ArrayList<>();
        for (DataFile dataFile : allFiles) {
            if (sessionRecordId != null && sessionRecordId.equals(dataFile.getSessionId())) {
                result.add(dataFile);
                continue;
            }
            if (dataFile.getObjectKey() != null && dataFile.getObjectKey().contains("sessions/" + sessionId)) {
                result.add(dataFile);
            }
        }
        return result;
    }

    private List<DataAssetResponse> findSessionAssets(Long taskId, Long sessionRecordId, String sessionId) {
        List<DataFile> sessionFiles = findSessionFiles(taskId, sessionRecordId, sessionId);
        List<DataAssetResponse> result = new ArrayList<>();
        for (DataFile dataFile : sessionFiles) {
            List<DataAsset> assets = dataAssetMapper.selectList(
                    new LambdaQueryWrapper<DataAsset>().eq(DataAsset::getFileId, dataFile.getId())
            );
            for (DataAsset asset : assets) {
                result.add(new DataAssetResponse(
                        asset.getId(),
                        asset.getTaskId(),
                        asset.getSessionId(),
                        asset.getSourceType(),
                        asset.getSourceKey(),
                        asset.getAssetType(),
                        asset.getDisplayName(),
                        asset.getFileId(),
                        dataFile.getOriginalFilename(),
                        dataFile.getFileExt(),
                        dataFile.getContentType(),
                        dataFile.getFileSize(),
                        dataFile.getUploadStatus(),
                        asset.getExternalPath(),
                        asset.getFileFormat(),
                        asset.getSizeRemark(),
                        asset.getDescription(),
                        asset.getOperatorRemark(),
                        asset.getProducedByJobId(),
                        asset.getCreatedAt(),
                        dataFile.getObjectKey(),
                        dataFile.getStorageUrl()
                ));
            }
        }
        return result;
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException exception) {
            throw new BizException("Failed to parse JSON", exception);
        }
    }

    private String resolveOrderByClause(String sortBy, String sortOrder) {
        boolean ascending = "asc".equalsIgnoreCase(trimToNull(sortOrder));
        String direction = ascending ? "ASC" : "DESC";
        String sortKey = trimToNull(sortBy);
        if (sortKey == null) {
            return "COALESCE(s.started_at, s.created_at) DESC, s.created_at DESC";
        }
        return switch (sortKey.toLowerCase(Locale.ROOT)) {
            case "startedat" -> "COALESCE(s.started_at, s.created_at) " + direction + ", s.created_at " + direction;
            case "createdat" -> "s.created_at " + direction;
            case "filecount" -> "COALESCE(fileAgg.file_count, 0) " + direction + ", s.created_at DESC";
            case "totalsize" -> "COALESCE(fileAgg.total_size, 0) " + direction + ", s.created_at DESC";
            default -> "COALESCE(s.started_at, s.created_at) DESC, s.created_at DESC";
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
