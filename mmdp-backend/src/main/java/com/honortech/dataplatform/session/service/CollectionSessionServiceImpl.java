package com.honortech.dataplatform.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.common.config.MinioProperties;
import com.honortech.dataplatform.common.enums.AssetSourceType;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.FileUploadStatus;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.util.FileNameUtils;
import com.honortech.dataplatform.common.util.MinioStorageClient;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import com.honortech.dataplatform.session.dto.SessionResponse;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CollectionSessionServiceImpl implements CollectionSessionService {

    private final CollectionSessionMapper sessionMapper;
    private final DataFileMapper dataFileMapper;
    private final DataAssetMapper dataAssetMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final MinioStorageClient minioStorageClient;
    private final MinioProperties minioProperties;
    private final ObjectMapper objectMapper;

    public CollectionSessionServiceImpl(
            CollectionSessionMapper sessionMapper,
            DataFileMapper dataFileMapper,
            DataAssetMapper dataAssetMapper,
            AcquisitionTaskService acquisitionTaskService,
            MinioStorageClient minioStorageClient,
            MinioProperties minioProperties,
            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.dataFileMapper = dataFileMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.minioStorageClient = minioStorageClient;
        this.minioProperties = minioProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public SessionResponse importSession(Long taskId, MultipartFile manifestFile, List<MultipartFile> dataFiles) {
        acquisitionTaskService.getTask(taskId);

        JsonNode manifest = parseManifest(manifestFile);
        String sessionId = manifest.get("sessionId").asText();
        String subjectCode = manifest.get("subjectCode").asText();
        String actionName = manifest.get("actionName").asText();
        String startedAtStr = manifest.get("startedAt").asText();
        long durationMs = manifest.get("durationMs").asLong(0);

        // Check for duplicate session
        CollectionSession existing = findBySessionId(sessionId);
        if (existing != null) {
            throw new BizException("Session already imported: " + sessionId);
        }

        LocalDateTime startedAt = parseDateTime(startedAtStr);
        LocalDateTime endedAt = startedAt.plusNanos(durationMs * 1_000_000);

        List<DataAssetResponse> assets = new ArrayList<>();
        JsonNode sources = manifest.get("sources");
        Iterator<String> sourceNames = sources.fieldNames();
        while (sourceNames.hasNext()) {
            String sourceName = sourceNames.next();
            JsonNode sourceInfo = sources.get(sourceName);
            String sourceType = sourceInfo.get("type").asText();
            String status = sourceInfo.has("status") ? sourceInfo.get("status").asText() : "completed";

            if ("missing".equals(status)) {
                continue;
            }

            // Upload JSONL file
            MultipartFile jsonlFile = findFile(dataFiles, sourceName, ".jsonl");
            if (jsonlFile != null) {
                DataFile df = uploadToMinIO(taskId, sessionId, sourceName, jsonlFile);
                assets.add(createAsset(taskId, df, sourceType.equals("imu") ? AssetType.MOCAP_CSV : AssetType.OTHER, sourceName + " metadata"));
            }

            // Upload MP4 file for video sources
            if ("video".equals(sourceType)) {
                MultipartFile mp4File = findFile(dataFiles, sourceName, ".mp4");
                if (mp4File != null) {
                    DataFile df = uploadToMinIO(taskId, sessionId, sourceName, mp4File);
                    assets.add(createAsset(taskId, df, AssetType.RGB_VIDEO_MP4, sourceName + " video"));
                }
            }
        }

        // Create session record
        CollectionSession session = new CollectionSession();
        session.setTaskId(taskId);
        session.setSessionId(sessionId);
        session.setSubjectCode(subjectCode);
        session.setActionName(actionName);
        session.setStartedAt(startedAt);
        session.setEndedAt(endedAt);
        session.setDurationMs(durationMs);
        session.setManifestJson(toJson(manifest));
        session.setUploadStatus("UPLOADED");
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        acquisitionTaskService.updateStatus(taskId, TaskStatus.UPLOADED);

        String taskName = acquisitionTaskService.getTask(taskId).getTaskName();

        return new SessionResponse(
                session.getId(), session.getTaskId(), taskName, session.getSessionId(),
                session.getSubjectCode(), session.getActionName(),
                session.getStartedAt(), session.getEndedAt(), session.getDurationMs(),
                session.getUploadStatus(), session.getCreatedAt(), assets
        );
    }

    @Override
    public List<SessionResponse> listByTaskId(Long taskId) {
        String taskName = acquisitionTaskService.getTask(taskId).getTaskName();
        List<CollectionSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .eq(CollectionSession::getTaskId, taskId)
                        .orderByDesc(CollectionSession::getCreatedAt)
        );
        List<SessionResponse> result = new ArrayList<>();
        for (CollectionSession s : sessions) {
            List<DataAssetResponse> assets = findSessionAssets(s.getTaskId(), s.getSessionId());
            result.add(new SessionResponse(
                    s.getId(), s.getTaskId(), taskName, s.getSessionId(),
                    s.getSubjectCode(), s.getActionName(),
                    s.getStartedAt(), s.getEndedAt(), s.getDurationMs(),
                    s.getUploadStatus(), s.getCreatedAt(), assets
            ));
        }
        return result;
    }

    @Override
    public List<SessionResponse> listAll() {
        List<CollectionSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .orderByDesc(CollectionSession::getCreatedAt)
        );
        List<SessionResponse> result = new ArrayList<>();
        for (CollectionSession s : sessions) {
            String taskName;
            try {
                taskName = acquisitionTaskService.getTask(s.getTaskId()).getTaskName();
            } catch (Exception e) {
                taskName = "Task-" + s.getTaskId();
            }
            List<DataAssetResponse> assets = findSessionAssets(s.getTaskId(), s.getSessionId());
            result.add(new SessionResponse(
                    s.getId(), s.getTaskId(), taskName, s.getSessionId(),
                    s.getSubjectCode(), s.getActionName(),
                    s.getStartedAt(), s.getEndedAt(), s.getDurationMs(),
                    s.getUploadStatus(), s.getCreatedAt(), assets
            ));
        }
        return result;
    }

    @Override
    public CollectionSession getBySessionId(String sessionId) {
        CollectionSession session = findBySessionId(sessionId);
        if (session == null) {
            throw new BizException("Session not found: " + sessionId);
        }
        return session;
    }

    @Override
    public SessionPlaybackResponse getPlaybackData(String sessionId) {
        CollectionSession session = getBySessionId(sessionId);
        JsonNode manifest = parseJson(session.getManifestJson());

        List<DataFile> sessionFiles = findSessionFiles(session.getTaskId(), sessionId);
        Map<String, SessionPlaybackResponse.PlaybackSource> sources = new HashMap<>();

        JsonNode sourcesNode = manifest.get("sources");
        Iterator<String> sourceNames = sourcesNode.fieldNames();
        while (sourceNames.hasNext()) {
            String sourceName = sourceNames.next();
            JsonNode sourceInfo = sourcesNode.get(sourceName);
            String type = sourceInfo.get("type").asText();
            String status = sourceInfo.has("status") ? sourceInfo.get("status").asText() : "completed";

            if ("missing".equals(status)) {
                continue;
            }

            String jsonlUrl = null;
            String videoUrl = null;
            Double fps = null;
            Integer sampleCount = null;
            Double sampleRate = null;
            String label = sourceName;

            if (sourceInfo.has("fps")) {
                fps = sourceInfo.get("fps").asDouble();
            }
            if (sourceInfo.has("sampleCount")) {
                sampleCount = sourceInfo.get("sampleCount").asInt();
            }
            if (sourceInfo.has("sampleRate")) {
                sampleRate = sourceInfo.get("sampleRate").asDouble();
            }

            // Match files by source name in filename
            for (DataFile df : sessionFiles) {
                String filename = df.getOriginalFilename().toLowerCase(Locale.ROOT);
                if (!filename.contains(sourceName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (filename.endsWith(".jsonl")) {
                    jsonlUrl = "/api/files/" + df.getId() + "/download";
                } else if (filename.endsWith(".mp4")) {
                    videoUrl = "/api/files/" + df.getId() + "/download";
                }
            }

            sources.put(sourceName, new SessionPlaybackResponse.PlaybackSource(
                    type, label, videoUrl, fps, sampleCount, jsonlUrl, sampleRate
            ));
        }

        return new SessionPlaybackResponse(
                session.getSessionId(), session.getTaskId(),
                session.getSubjectCode(), session.getActionName(),
                session.getStartedAt(), session.getDurationMs(), sources
        );
    }

    // ── helpers ────────────────────────────────────────────────

    private DataFile uploadToMinIO(Long taskId, String sessionId, String sourceName, MultipartFile file) {
        String originalFilename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String fileExt = FileNameUtils.getExtension(originalFilename);
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String objectKey = "tasks/" + taskId + "/sessions/" + sessionId + "/"
                + sourceName + "/" + UUID.randomUUID() + "." + fileExt;
        byte[] bytes = readBytes(file);

        String storageUrl = minioStorageClient.upload(objectKey, bytes, contentType);

        DataFile dataFile = new DataFile();
        dataFile.setTaskId(taskId);
        dataFile.setOriginalFilename(originalFilename);
        dataFile.setFileExt(fileExt);
        dataFile.setContentType(contentType);
        dataFile.setFileSize(file.getSize());
        dataFile.setBucketName(minioProperties.getBucket());
        dataFile.setObjectKey(objectKey);
        dataFile.setStorageUrl(storageUrl);
        dataFile.setUploadStatus(FileUploadStatus.SUCCESS.name());
        dataFile.setCreatedAt(LocalDateTime.now());
        dataFileMapper.insert(dataFile);
        return dataFile;
    }

    private DataAssetResponse createAsset(Long taskId, DataFile file, AssetType assetType, String displayName) {
        DataAsset asset = new DataAsset();
        asset.setTaskId(taskId);
        asset.setSourceType(AssetSourceType.UPLOADED_FILE.name());
        asset.setAssetType(assetType.name());
        asset.setDisplayName(displayName);
        asset.setFileId(file.getId());
        asset.setFileFormat(file.getFileExt());
        asset.setCreatedAt(LocalDateTime.now());
        dataAssetMapper.insert(asset);

        return new DataAssetResponse(
                asset.getId(), asset.getTaskId(), asset.getSourceType(), asset.getAssetType(),
                asset.getDisplayName(), asset.getFileId(),
                file.getOriginalFilename(), file.getFileExt(), file.getContentType(),
                file.getFileSize(), file.getUploadStatus(),
                asset.getExternalPath(), asset.getFileFormat(), asset.getSizeRemark(),
                asset.getDescription(), asset.getOperatorRemark(),
                asset.getProducedByJobId(), asset.getCreatedAt(),
                file.getObjectKey(), file.getStorageUrl()
        );
    }

    private List<DataFile> findSessionFiles(Long taskId, String sessionId) {
        List<DataFile> allFiles = dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>().eq(DataFile::getTaskId, taskId)
        );
        List<DataFile> result = new ArrayList<>();
        for (DataFile df : allFiles) {
            if (df.getObjectKey() != null && df.getObjectKey().contains("sessions/" + sessionId)) {
                result.add(df);
            }
        }
        return result;
    }

    private List<DataAssetResponse> findSessionAssets(Long taskId, String sessionId) {
        List<DataFile> sessionFiles = findSessionFiles(taskId, sessionId);
        List<DataAssetResponse> result = new ArrayList<>();
        for (DataFile df : sessionFiles) {
            List<DataAsset> assets = dataAssetMapper.selectList(
                    new LambdaQueryWrapper<DataAsset>().eq(DataAsset::getFileId, df.getId())
            );
            for (DataAsset asset : assets) {
                result.add(new DataAssetResponse(
                        asset.getId(), asset.getTaskId(), asset.getSourceType(), asset.getAssetType(),
                        asset.getDisplayName(), asset.getFileId(),
                        df.getOriginalFilename(), df.getFileExt(), df.getContentType(),
                        df.getFileSize(), df.getUploadStatus(),
                        asset.getExternalPath(), asset.getFileFormat(), asset.getSizeRemark(),
                        asset.getDescription(), asset.getOperatorRemark(),
                        asset.getProducedByJobId(), asset.getCreatedAt(),
                        df.getObjectKey(), df.getStorageUrl()
                ));
            }
        }
        return result;
    }

    private CollectionSession findBySessionId(String sessionId) {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<CollectionSession>()
                        .eq(CollectionSession::getSessionId, sessionId)
        );
    }

    private MultipartFile findFile(List<MultipartFile> files, String sourceName, String extension) {
        String nameLower = sourceName.toLowerCase(Locale.ROOT);
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null) continue;
            String fnameLower = filename.toLowerCase(Locale.ROOT);
            if (fnameLower.contains(nameLower) && fnameLower.endsWith(extension)) {
                return file;
            }
        }
        return null;
    }

    private JsonNode parseManifest(MultipartFile file) {
        try {
            return objectMapper.readTree(file.getBytes());
        } catch (IOException e) {
            throw new BizException("Failed to parse manifest JSON", e);
        }
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException e) {
            throw new BizException("Failed to parse JSON", e);
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new BizException("Failed to serialize JSON", e);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException("Failed to read uploaded file", e);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            if (value.contains("T")) {
                if (value.length() > 19) {
                    value = value.substring(0, 19);
                }
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            }
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
