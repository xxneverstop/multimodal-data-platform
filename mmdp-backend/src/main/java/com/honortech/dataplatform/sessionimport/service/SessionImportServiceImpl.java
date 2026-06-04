package com.honortech.dataplatform.sessionimport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.collector.entity.CollectorClient;
import com.honortech.dataplatform.collector.service.CollectorClientService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.FileUploadStatus;
import com.honortech.dataplatform.common.enums.SessionImportStatus;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.common.storage.StoredFile;
import com.honortech.dataplatform.common.util.BusinessCodeGenerator;
import com.honortech.dataplatform.common.util.FileNameUtils;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.profile.rule.ArchiveRuleResolver;
import com.honortech.dataplatform.profile.rule.PackageRuleResolver;
import com.honortech.dataplatform.profile.rule.ParserRuleResolver;
import com.honortech.dataplatform.profile.rule.ProfileRuleRegistry;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.sessionimport.dto.NormalizedSessionManifest;
import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.entity.SessionImportRecord;
import com.honortech.dataplatform.sessionimport.mapper.SessionImportRecordMapper;
import com.honortech.dataplatform.subject.entity.Subject;
import com.honortech.dataplatform.subject.service.SubjectService;
import com.honortech.dataplatform.task.dto.CreateTaskRequest;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.mapper.AcquisitionTaskMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SessionImportServiceImpl implements SessionImportService {

    public static final String SOURCE_ENDPOINT_SESSION_IMPORTS = "SESSION_IMPORTS";
    public static final String SOURCE_ENDPOINT_LEGACY_TASK_ROUTE = "LEGACY_TASK_ROUTE";

    private static final Logger log = LoggerFactory.getLogger(SessionImportServiceImpl.class);

    private final CollectionSessionMapper sessionMapper;
    private final DataFileMapper dataFileMapper;
    private final SessionImportRecordMapper sessionImportRecordMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final AcquisitionTaskMapper acquisitionTaskMapper;
    private final DataAssetService dataAssetService;
    private final StorageRouter storageRouter;
    private final ObjectMapper objectMapper;
    private final SubjectService subjectService;
    private final CollectionProfileService collectionProfileService;
    private final CollectorClientService collectorClientService;
    private final ProfileRuleRegistry profileRuleRegistry;
    private final BusinessCodeGenerator businessCodeGenerator;

    public SessionImportServiceImpl(
            CollectionSessionMapper sessionMapper,
            DataFileMapper dataFileMapper,
            SessionImportRecordMapper sessionImportRecordMapper,
            AcquisitionTaskService acquisitionTaskService,
            AcquisitionTaskMapper acquisitionTaskMapper,
            DataAssetService dataAssetService,
            StorageRouter storageRouter,
            ObjectMapper objectMapper,
            SubjectService subjectService,
            CollectionProfileService collectionProfileService,
            CollectorClientService collectorClientService,
            ProfileRuleRegistry profileRuleRegistry,
            BusinessCodeGenerator businessCodeGenerator) {
        this.sessionMapper = sessionMapper;
        this.dataFileMapper = dataFileMapper;
        this.sessionImportRecordMapper = sessionImportRecordMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.acquisitionTaskMapper = acquisitionTaskMapper;
        this.dataAssetService = dataAssetService;
        this.storageRouter = storageRouter;
        this.objectMapper = objectMapper;
        this.subjectService = subjectService;
        this.collectionProfileService = collectionProfileService;
        this.collectorClientService = collectorClientService;
        this.profileRuleRegistry = profileRuleRegistry;
        this.businessCodeGenerator = businessCodeGenerator;
    }

    @Override
    public SessionImportResponse importSession(SessionImportRequestContext context) {
        if (context.manifestFile() == null || context.manifestFile().isEmpty()) {
            throw new BizException("Manifest file must not be empty");
        }
        if ((context.archiveFile() == null || context.archiveFile().isEmpty())
                && (context.files() == null || context.files().isEmpty())) {
            throw new BizException("Session import requires archive or files");
        }

        NormalizedSessionManifest manifest = normalizeManifest(context.manifestFile());
        CollectionSession existingSession = findSessionByLocalSessionId(manifest.localSessionId());
        if (existingSession != null) {
            SessionImportRecord existingRecord = ensureImportRecordForExistingSession(existingSession, manifest, context.sourceEndpoint());
            return new SessionImportResponse(existingRecord.getId(), existingSession.getTaskId(), existingSession.getId(), existingRecord.getStatus(), true);
        }

        CollectorClient collectorClient = collectorClientService.resolveByCode(manifest.clientId());
        AcquisitionTask task = resolveTask(context, manifest, collectorClient);
        CollectionProfile profile = resolveProfile(task, manifest);
        Subject subject = subjectService.resolveSubject(manifest.subjectCode(), manifest.subjectName());
        SessionImportRecord importRecord = createImportRecord(task, manifest, collectorClient, context.sourceEndpoint());

        try {
            updateImportRecord(importRecord, SessionImportStatus.PROCESSING, "Processing session import");
            List<ImportedBinary> structuredFiles = context.archiveFile() != null && !context.archiveFile().isEmpty()
                    ? extractArchiveEntries(context.archiveFile())
                    : toImportedFiles(context.files());

            PackageRuleResolver packageRule = profileRuleRegistry.getPackageRule(profile.getPackageRuleCode());
            packageRule.validate(profile, manifest, structuredFiles.stream().map(ImportedBinary::originalFilename).toList());
            ArchiveRuleResolver archiveRule = profileRuleRegistry.getArchiveRule(profile.getArchiveRuleCode());
            ParserRuleResolver parserRule = profileRuleRegistry.getParserRule(profile.getParserRuleCode());
            List<CollectionProfileSource> profileSources = collectionProfileService.listSourcesByProfileId(profile.getId());

            CollectionSession session = createSessionRecord(task, subject, profile, collectorClient, manifest);
            importRecord.setSessionRecordId(session.getId());

            DataFile archiveFile = saveArchiveIfPresent(task.getId(), session.getId(), manifest.localSessionId(), context.archiveFile(), profile, archiveRule);
            if (archiveFile != null) {
                importRecord.setArchiveFileId(archiveFile.getId());
                dataAssetService.createAcquisitionAsset(task.getId(), session.getId(), "archive", archiveFile, AssetType.SESSION_ARCHIVE_ZIP);
            }

            for (ImportedBinary file : structuredFiles) {
                String sourceKey = parserRule.resolveSourceKey(profile, profileSources, file.originalFilename());
                DataFile dataFile = storeStructuredFile(task.getId(), session.getId(), manifest.localSessionId(), file, profile, archiveRule, sourceKey);
                AssetType assetType = determineAssetType(profileSources, sourceKey, file.originalFilename());
                dataAssetService.createAcquisitionAsset(task.getId(), session.getId(), sourceKey, dataFile, assetType);
            }

            session.setUploadStatus(SessionImportStatus.IMPORTED.name());
            session.setSessionStatus("PLAYABLE");
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            acquisitionTaskService.updateStatus(task.getId(), TaskStatus.UPLOADED);
            updateImportRecord(importRecord, SessionImportStatus.IMPORTED, "Session imported");
            return new SessionImportResponse(importRecord.getId(), task.getId(), session.getId(), importRecord.getStatus(), false);
        } catch (RuntimeException exception) {
            updateImportRecord(importRecord, SessionImportStatus.FAILED, exception.getMessage());
            throw exception;
        }
    }

    private NormalizedSessionManifest normalizeManifest(MultipartFile manifestFile) {
        JsonNode manifest = parseManifest(manifestFile);
        JsonNode localRefs = nodeOrNull(manifest, "localRefs");
        JsonNode platformRefs = nodeOrNull(manifest, "platformRefs");
        JsonNode task = nodeOrNull(manifest, "task");
        JsonNode subject = nodeOrNull(manifest, "subject");
        JsonNode action = nodeOrNull(manifest, "action");
        JsonNode session = nodeOrNull(manifest, "session");

        String localTaskId = firstNonBlank(textValue(localRefs, "localTaskId"), textValue(manifest, "taskId"));
        String localSessionId = firstNonBlank(textValue(localRefs, "localSessionId"), textValue(manifest, "sessionId"));
        Long platformTaskId = longValue(platformRefs, "platformTaskId");
        String platformTaskCode = textValue(platformRefs, "platformTaskCode");
        String taskName = firstNonBlank(textValue(task, "name"), textValue(manifest, "taskName"));
        String profileCode = firstNonBlank(textValue(task, "profileCode"), textValue(manifest, "profileCode"));
        String profileName = firstNonBlank(textValue(task, "profileName"), textValue(manifest, "profileName"));
        String subjectName = firstNonBlank(textValue(subject, "name"), textValue(manifest, "subjectName"));
        String subjectCode = firstNonBlank(textValue(subject, "code"), textValue(manifest, "subjectCode"));
        String actionName = firstNonBlank(textValue(action, "name"), textValue(manifest, "actionName"));
        String actionCode = textValue(action, "code");
        String startedAtValue = firstNonBlank(textValue(session, "startedAt"), textValue(manifest, "startedAt"));
        String endedAtValue = firstNonBlank(textValue(session, "endedAt"), textValue(manifest, "endedAt"));
        Long durationMs = firstNonNull(longValue(session, "durationMs"), longValue(manifest, "durationMs"), 0L);
        String timestampPolicy = firstNonBlank(textValue(session, "timestampPolicy"), textValue(manifest, "timestampPolicy"), "hostReceiveTimestamp");
        String clientId = firstNonBlank(textValue(manifest, "clientId"), textValue(manifest, "collectorClientCode"));
        JsonNode sources = nodeOrNull(manifest, "sources");
        JsonNode artifacts = nodeOrNull(manifest, "artifacts");

        validateManifest(localSessionId, taskName, profileCode, subjectName, subjectCode, actionName, startedAtValue, sources);
        LocalDateTime startedAt = parseDateTime(startedAtValue);
        LocalDateTime endedAt = endedAtValue == null ? startedAt.plusNanos(durationMs * 1_000_000) : parseDateTime(endedAtValue);

        return new NormalizedSessionManifest(
                textValue(manifest, "schemaVersion"),
                clientId,
                localTaskId,
                localSessionId,
                platformTaskId,
                platformTaskCode,
                taskName,
                profileCode,
                profileName,
                subjectName,
                subjectCode,
                actionName,
                actionCode,
                startedAt,
                endedAt,
                durationMs,
                timestampPolicy,
                sources,
                artifacts,
                manifest,
                extractSourceTypeMap(sources)
        );
    }

    private void validateManifest(
            String localSessionId,
            String taskName,
            String profileCode,
            String subjectName,
            String subjectCode,
            String actionName,
            String startedAtValue,
            JsonNode sources) {
        if (isBlank(localSessionId)) {
            throw new BizException("Manifest missing localSessionId/sessionId");
        }
        if (isBlank(taskName)) {
            throw new BizException("Manifest missing task.name/taskName");
        }
        if (isBlank(profileCode)) {
            throw new BizException("Manifest missing task.profileCode/profileCode");
        }
        if (isBlank(subjectName) && isBlank(subjectCode)) {
            throw new BizException("Manifest missing subject.name/subject.code");
        }
        if (isBlank(actionName)) {
            throw new BizException("Manifest missing action.name/actionName");
        }
        if (isBlank(startedAtValue)) {
            throw new BizException("Manifest missing session.startedAt/startedAt");
        }
        if (sources == null || !sources.isObject() || !sources.fieldNames().hasNext()) {
            throw new BizException("Manifest missing sources");
        }
    }

    private AcquisitionTask resolveTask(SessionImportRequestContext context, NormalizedSessionManifest manifest, CollectorClient collectorClient) {
        Long explicitPlatformTaskId = manifest.platformTaskId();
        if (explicitPlatformTaskId != null) {
            if (context.fallbackPlatformTaskId() != null && !Objects.equals(explicitPlatformTaskId, context.fallbackPlatformTaskId())) {
                log.warn("Legacy taskId {} conflicts with manifest platformTaskId {}, using manifest value",
                        context.fallbackPlatformTaskId(), explicitPlatformTaskId);
            }
            return acquisitionTaskService.getTask(explicitPlatformTaskId);
        }
        if (SOURCE_ENDPOINT_LEGACY_TASK_ROUTE.equals(context.sourceEndpoint()) && context.fallbackPlatformTaskId() != null) {
            return acquisitionTaskService.getTask(context.fallbackPlatformTaskId());
        }
        CollectionProfile profile = collectionProfileService.getRequiredByCode(manifest.profileCode());
        CreateTaskRequest request = new CreateTaskRequest(
                manifest.taskName(),
                manifest.subjectCode(),
                manifest.subjectName(),
                manifest.actionName(),
                profile.getId(),
                profile.getDeviceGroupCode(),
                profile.getModalityGroupCode(),
                manifest.startedAt().toLocalDate(),
                null,
                null,
                null,
                "Auto-created from collector session import"
        );
        AcquisitionTask task = acquisitionTaskService.createTask(request);
        task.setTaskSource("COLLECTOR_IMPORT");
        task.setCollectorClientId(collectorClient == null ? null : collectorClient.getId());
        task.setUpdatedAt(LocalDateTime.now());
        acquisitionTaskMapper.updateById(task);
        return task;
    }

    private CollectionProfile resolveProfile(AcquisitionTask task, NormalizedSessionManifest manifest) {
        if (task.getProfileId() != null) {
            return collectionProfileService.getRequiredById(task.getProfileId());
        }
        if (!isBlank(manifest.profileCode())) {
            CollectionProfile profile = collectionProfileService.getRequiredByCode(manifest.profileCode());
            task.setProfileId(profile.getId());
            task.setUpdatedAt(LocalDateTime.now());
            acquisitionTaskMapper.updateById(task);
            return profile;
        }
        throw new BizException("Task has no profile and manifest profileCode is missing");
    }

    private SessionImportRecord createImportRecord(
            AcquisitionTask task,
            NormalizedSessionManifest manifest,
            CollectorClient collectorClient,
            String sourceEndpoint) {
        SessionImportRecord record = new SessionImportRecord();
        record.setTaskId(task.getId());
        record.setCollectorClientId(collectorClient == null ? null : collectorClient.getId());
        record.setLocalTaskId(manifest.localTaskId());
        record.setLocalSessionId(manifest.localSessionId());
        record.setStatus(SessionImportStatus.RECEIVED.name());
        record.setManifestJson(toJson(manifest.rawManifest()));
        record.setSourceEndpoint(sourceEndpoint);
        record.setRequestId(UUID.randomUUID().toString());
        record.setMessage("Import received");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        sessionImportRecordMapper.insert(record);
        return record;
    }

    private SessionImportRecord ensureImportRecordForExistingSession(
            CollectionSession existingSession,
            NormalizedSessionManifest manifest,
            String sourceEndpoint) {
        SessionImportRecord record = findImportRecordByLocalSessionId(manifest.localSessionId());
        if (record != null) {
            return record;
        }
        record = new SessionImportRecord();
        record.setTaskId(existingSession.getTaskId());
        record.setSessionRecordId(existingSession.getId());
        record.setLocalTaskId(manifest.localTaskId());
        record.setLocalSessionId(manifest.localSessionId());
        record.setStatus(SessionImportStatus.IMPORTED.name());
        record.setManifestJson(toJson(manifest.rawManifest()));
        record.setSourceEndpoint(sourceEndpoint);
        record.setRequestId(UUID.randomUUID().toString());
        record.setMessage("Session already imported");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        sessionImportRecordMapper.insert(record);
        return record;
    }

    private void updateImportRecord(SessionImportRecord record, SessionImportStatus status, String message) {
        record.setStatus(status.name());
        record.setMessage(trimToLength(message, 512));
        record.setUpdatedAt(LocalDateTime.now());
        sessionImportRecordMapper.updateById(record);
    }

    private CollectionSession createSessionRecord(
            AcquisitionTask task,
            Subject subject,
            CollectionProfile profile,
            CollectorClient collectorClient,
            NormalizedSessionManifest manifest) {
        CollectionSession session = new CollectionSession();
        session.setSessionCode(businessCodeGenerator.next("SESS"));
        session.setTaskId(task.getId());
        session.setSubjectId(subject.getId());
        session.setSessionId(manifest.localSessionId());
        session.setLocalTaskId(manifest.localTaskId());
        session.setSubjectCode(subject.getSubjectCode());
        session.setSubjectCodeSnapshot(subject.getSubjectCode());
        session.setActionName(manifest.actionName());
        session.setActionNameSnapshot(manifest.actionName());
        session.setProfileId(profile.getId());
        session.setCollectorClientId(collectorClient == null ? null : collectorClient.getId());
        session.setStartedAt(manifest.startedAt());
        session.setEndedAt(manifest.endedAt());
        session.setDurationMs(manifest.durationMs());
        session.setTimestampPolicy(manifest.timestampPolicy());
        session.setManifestJson(toJson(manifest.rawManifest()));
        session.setUploadStatus(SessionImportStatus.PROCESSING.name());
        session.setSessionStatus("IMPORTED");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    private DataFile saveArchiveIfPresent(
            Long taskId,
            Long sessionRecordId,
            String localSessionId,
            MultipartFile archiveFile,
            CollectionProfile profile,
            ArchiveRuleResolver archiveRule) {
        if (archiveFile == null || archiveFile.isEmpty()) {
            return null;
        }
        String originalFilename = firstNonBlank(archiveFile.getOriginalFilename(), localSessionId + ".zip");
        byte[] bytes = readBytes(archiveFile);
        String objectKey = "tasks/" + taskId + "/sessions/" + localSessionId + "/archive/" + UUID.randomUUID() + ".zip";
        return saveDataFile(taskId, sessionRecordId, archiveRule.fileRoleForArchive(profile), "archive", originalFilename,
                originalFilename, "zip", firstNonBlank(archiveFile.getContentType(), "application/zip"), bytes, objectKey);
    }

    private List<ImportedBinary> extractArchiveEntries(MultipartFile archiveFile) {
        List<ImportedBinary> extractedFiles = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveFile.getBytes()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedPath = normalizeEntryPath(entry.getName());
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                zipInputStream.transferTo(outputStream);
                extractedFiles.add(new ImportedBinary(normalizedPath, detectContentType(normalizedPath), outputStream.toByteArray()));
            }
        } catch (IOException exception) {
            throw new BizException("Failed to read archive ZIP", exception);
        }
        return extractedFiles;
    }

    private List<ImportedBinary> toImportedFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<ImportedBinary> importedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalFilename = firstNonBlank(file.getOriginalFilename(), "unknown");
            importedFiles.add(new ImportedBinary(originalFilename, firstNonBlank(file.getContentType(), detectContentType(originalFilename)), readBytes(file)));
        }
        return importedFiles;
    }

    private DataFile storeStructuredFile(
            Long taskId,
            Long sessionRecordId,
            String localSessionId,
            ImportedBinary file,
            CollectionProfile profile,
            ArchiveRuleResolver archiveRule,
            String sourceKey) {
        String normalizedPath = normalizeEntryPath(file.originalFilename());
        String objectKey = "tasks/" + taskId + "/sessions/" + localSessionId + "/extracted/" + normalizedPath;
        return saveDataFile(
                taskId,
                sessionRecordId,
                archiveRule.fileRoleForExtracted(profile, normalizedPath),
                sourceKey,
                fileName(normalizedPath),
                normalizedPath,
                FileNameUtils.getExtension(normalizedPath),
                firstNonBlank(file.contentType(), "application/octet-stream"),
                file.bytes(),
                objectKey
        );
    }

    private DataFile saveDataFile(
            Long taskId,
            Long sessionRecordId,
            String fileRole,
            String sourceKey,
            String originalFilename,
            String relativePath,
            String fileExt,
            String contentType,
            byte[] bytes,
            String objectKey) {
        StoredFile storedFile = storageRouter.defaultService().upload(objectKey, bytes, contentType, originalFilename);
        DataFile dataFile = new DataFile();
        dataFile.setTaskId(taskId);
        dataFile.setSessionId(sessionRecordId);
        dataFile.setFileRole(fileRole);
        dataFile.setSourceKey(sourceKey);
        dataFile.setOriginalFilename(originalFilename);
        dataFile.setRelativePath(relativePath);
        dataFile.setFileExt(fileExt);
        dataFile.setContentType(contentType);
        dataFile.setFileSize((long) bytes.length);
        dataFile.setSha256(null);
        dataFile.setStorageProvider(storedFile.storageProvider().name());
        dataFile.setBucketName(storedFile.bucketName());
        dataFile.setObjectKey(storedFile.objectKey());
        dataFile.setStorageUrl(storedFile.storageUrl());
        dataFile.setUploadStatus(FileUploadStatus.SUCCESS.name());
        dataFile.setCreatedAt(LocalDateTime.now());
        dataFileMapper.insert(dataFile);
        return dataFile;
    }

    private AssetType determineAssetType(List<CollectionProfileSource> profileSources, String sourceKey, String filename) {
        for (CollectionProfileSource source : profileSources) {
            if (source.getSourceKey().equalsIgnoreCase(firstNonBlank(sourceKey, ""))) {
                return AssetType.fromNullable(source.getParsedAssetType());
            }
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".zip")) {
            return AssetType.SESSION_ARCHIVE_ZIP;
        }
        if (lowerName.endsWith(".mp4")) {
            return AssetType.RGB_VIDEO_MP4;
        }
        if (lowerName.endsWith(".jsonl")) {
            return AssetType.MOCAP_CSV;
        }
        return AssetType.OTHER;
    }

    private Map<String, String> extractSourceTypeMap(JsonNode sources) {
        Map<String, String> sourceTypes = new LinkedHashMap<>();
        if (sources == null || !sources.isObject()) {
            return sourceTypes;
        }
        Iterator<String> fieldNames = sources.fieldNames();
        while (fieldNames.hasNext()) {
            String sourceName = fieldNames.next();
            JsonNode sourceInfo = sources.get(sourceName);
            sourceTypes.put(sourceName, textValue(sourceInfo, "type"));
        }
        return sourceTypes;
    }

    private CollectionSession findSessionByLocalSessionId(String localSessionId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<CollectionSession>()
                .eq(CollectionSession::getSessionId, localSessionId));
    }

    private SessionImportRecord findImportRecordByLocalSessionId(String localSessionId) {
        return sessionImportRecordMapper.selectOne(new LambdaQueryWrapper<SessionImportRecord>()
                .eq(SessionImportRecord::getLocalSessionId, localSessionId));
    }

    private JsonNode parseManifest(MultipartFile file) {
        try {
            return objectMapper.readTree(file.getBytes());
        } catch (IOException exception) {
            throw new BizException("Failed to parse manifest JSON", exception);
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException exception) {
            throw new BizException("Failed to serialize JSON", exception);
        }
    }

    private String normalizeEntryPath(String entryName) {
        String normalized = firstNonBlank(entryName, "unknown").replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("../") || normalized.contains("..\\" ) || normalized.startsWith("..")) {
            throw new BizException("Archive contains unsafe entry path: " + entryName);
        }
        return normalized;
    }

    private String detectContentType(String filename) {
        String contentType = URLConnection.guessContentTypeFromName(filename);
        return contentType == null ? "application/octet-stream" : contentType;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BizException("Failed to read uploaded file", exception);
        }
    }

    private JsonNode nodeOrNull(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? null : child;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = nodeOrNull(node, fieldName);
        if (value == null || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode value = nodeOrNull(node, fieldName);
        if (value == null || value.isContainerNode()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = value.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            throw new BizException("Manifest field '" + fieldName + "' must be a number");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            String normalized = value.trim();
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.contains("T")) {
                if (normalized.length() > 19) {
                    normalized = normalized.substring(0, 19);
                }
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            }
            return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception exception) {
            throw new BizException("Invalid date time value: " + value, exception);
        }
    }

    private String fileName(String path) {
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record ImportedBinary(String originalFilename, String contentType, byte[] bytes) {
    }
}
