package com.honortech.dataplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.FileUploadStatus;
import com.honortech.dataplatform.common.enums.QcStatus;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.ObjectStat;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.common.storage.StorageProperties;
import com.honortech.dataplatform.common.storage.StoredFile;
import com.honortech.dataplatform.common.storage.TemporaryCredentials;
import com.honortech.dataplatform.common.util.ArchiveUtils;
import com.honortech.dataplatform.common.util.BusinessCodeGenerator;
import com.honortech.dataplatform.common.util.FileNameUtils;
import com.honortech.dataplatform.file.dto.CompleteDirectUploadRequest;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadRequest;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.qc.dto.QcExecutionResult;
import com.honortech.dataplatform.qc.entity.QcReport;
import com.honortech.dataplatform.qc.mapper.QcReportMapper;
import com.honortech.dataplatform.qc.service.QcInspectionService;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.subject.entity.Subject;
import com.honortech.dataplatform.subject.service.SubjectService;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DataFileServiceImpl implements DataFileService {

    private static final Logger log = LoggerFactory.getLogger(DataFileServiceImpl.class);

    private final AcquisitionTaskService acquisitionTaskService;
    private final DataFileMapper dataFileMapper;
    private final QcReportMapper qcReportMapper;
    private final QcInspectionService qcInspectionService;
    private final StorageRouter storageRouter;
    private final ObjectMapper objectMapper;
    private final DataAssetService dataAssetService;
    private final CollectionSessionMapper sessionMapper;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final SubjectService subjectService;
    private final StorageProperties storageProperties;

    public DataFileServiceImpl(
            AcquisitionTaskService acquisitionTaskService,
            DataFileMapper dataFileMapper,
            QcReportMapper qcReportMapper,
            QcInspectionService qcInspectionService,
            StorageRouter storageRouter,
            ObjectMapper objectMapper,
            DataAssetService dataAssetService,
            CollectionSessionMapper sessionMapper,
            BusinessCodeGenerator businessCodeGenerator,
            SubjectService subjectService,
            StorageProperties storageProperties) {
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataFileMapper = dataFileMapper;
        this.qcReportMapper = qcReportMapper;
        this.qcInspectionService = qcInspectionService;
        this.storageRouter = storageRouter;
        this.objectMapper = objectMapper;
        this.dataAssetService = dataAssetService;
        this.sessionMapper = sessionMapper;
        this.businessCodeGenerator = businessCodeGenerator;
        this.subjectService = subjectService;
        this.storageProperties = storageProperties;
    }

    @Override
    @Transactional
    public FileUploadResponse uploadTaskFile(Long taskId, MultipartFile file, String assetTypeValue) {
        if (file == null || file.isEmpty()) {
            throw new BizException("No file to upload");
        }
        AcquisitionTask task = acquisitionTaskService.getTask(taskId);
        CollectionSession session = resolveOrCreateSession(taskId, null, task);
        ImportedBinary binary = toImportedBinary(file);
        AssetType assetType = AssetType.fromNullable(assetTypeValue);
        DataFile dataFile = storeFile(task, session, binary, assetType);
        dataAssetService.createUploadedAssetIfAbsent(taskId, dataFile, assetType);
        runQc(taskId, session.getId(), dataFile, binary, assetType);
        markUploadCompleted(session, taskId);
        return new FileUploadResponse(List.of(toResponse(dataFile)), session.getId(), session.getSessionCode());
    }

    @Override
    @Transactional
    public FileUploadResponse uploadTaskFiles(Long taskId, Long sessionId, List<MultipartFile> files, MultipartFile archive) {
        AcquisitionTask task = acquisitionTaskService.getTask(taskId);
        CollectionSession session = resolveOrCreateSession(taskId, sessionId, task);
        List<ImportedBinary> binaries = collectBinaries(files, archive);
        if (binaries.isEmpty()) {
            throw new BizException("No files to upload");
        }

        List<DataFileResponse> savedFiles = new ArrayList<>();
        for (ImportedBinary binary : binaries) {
            AssetType assetType = inferAssetType(binary.originalFilename());
            DataFile dataFile = storeFile(task, session, binary, assetType);
            savedFiles.add(toResponse(dataFile));
            dataAssetService.createUploadedAssetIfAbsent(taskId, dataFile, assetType);
            runQc(taskId, session.getId(), dataFile, binary, assetType);
        }

        markUploadCompleted(session, taskId);
        return new FileUploadResponse(savedFiles, session.getId(), session.getSessionCode());
    }

    @Override
    @Transactional
    public InitiateDirectUploadResponse initiateDirectUpload(Long taskId, InitiateDirectUploadRequest request) {
        AcquisitionTask task = acquisitionTaskService.getTask(taskId);
        CollectionSession session = resolveOrCreateSession(taskId, request.sessionId(), task);
        AssetType assetType = AssetType.fromNullable(request.assetType());
        String objectKey = FileNameUtils.buildObjectKey(
                task.getTaskCode(),
                session.getSessionCode(),
                "MANUAL_UPLOAD",
                "manual",
                request.fileName()
        );

        DataFile dataFile = new DataFile();
        dataFile.setTaskId(task.getId());
        dataFile.setSessionId(session.getId());
        dataFile.setFileRole("MANUAL_UPLOAD");
        dataFile.setSourceKey("manual");
        dataFile.setOriginalFilename(request.fileName());
        dataFile.setRelativePath(request.fileName());
        dataFile.setFileExt(FileNameUtils.getExtension(request.fileName()));
        dataFile.setContentType(request.contentType());
        dataFile.setFileSize(request.fileSize());
        dataFile.setSha256(null);
        dataFile.setAssetType(assetType.name());
        dataFile.setStorageProvider(storageRouter.defaultService().provider().name());
        dataFile.setBucketName(storageProperties.getOss().getBucket());
        dataFile.setObjectKey(objectKey);
        dataFile.setStorageUrl(buildOssStorageUrl(dataFile.getBucketName(), objectKey));
        dataFile.setUploadStatus(FileUploadStatus.PENDING.name());
        dataFile.setCreatedAt(LocalDateTime.now());
        dataFileMapper.insert(dataFile);

        String roleSessionName = buildRoleSessionName(dataFile.getId());
        TemporaryCredentials credentials = storageRouter.defaultService().assumeUploadCredentials(dataFile.getBucketName(), objectKey, roleSessionName);
        return new InitiateDirectUploadResponse(
                dataFile.getId(),
                session.getId(),
                session.getSessionCode(),
                dataFile.getBucketName(),
                storageProperties.getOss().getRegion(),
                storageProperties.getOss().getEndpoint(),
                objectKey,
                credentials.accessKeyId(),
                credentials.accessKeySecret(),
                credentials.securityToken(),
                credentials.expiration()
        );
    }

    @Override
    @Transactional
    public DataFileResponse completeDirectUpload(CompleteDirectUploadRequest request) {
        DataFile file = getFile(request.fileId());
        String status = file.getUploadStatus();
        if (FileUploadStatus.SUCCESS.name().equals(status)) {
            return toResponse(file);
        }
        if (FileUploadStatus.FAILED.name().equals(status)) {
            throw new BizException("File upload has already failed: " + file.getId());
        }
        if (!FileUploadStatus.PENDING.name().equals(status)) {
            throw new BizException("File is not pending direct upload completion: " + file.getId());
        }

        try {
            ObjectStat objectStat = storageRouter.defaultService().headObject(file.getBucketName(), file.getObjectKey());
            if (objectStat.size() != file.getFileSize()) {
                file.setUploadStatus(FileUploadStatus.FAILED.name());
                dataFileMapper.updateById(file);
                throw new BizException("Uploaded object size does not match the registered file size");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // OSS object not found or inaccessible — mark as FAILED
            file.setUploadStatus(FileUploadStatus.FAILED.name());
            dataFileMapper.updateById(file);
            throw new BizException("Failed to verify uploaded object in OSS: " + e.getMessage(), e);
        }

        file.setStorageUrl(buildOssStorageUrl(file.getBucketName(), file.getObjectKey()));
        file.setUploadStatus(FileUploadStatus.SUCCESS.name());
        dataFileMapper.updateById(file);

        AssetType assetType = resolveAssetType(file);
        DataAsset asset = dataAssetService.createUploadedAssetIfAbsent(file.getTaskId(), file, assetType);
        log.debug("Direct upload completion linked assetId={} for fileId={}", asset.getId(), file.getId());

        CollectionSession session = sessionMapper.selectById(file.getSessionId());
        if (session != null) {
            session.setUploadStatus("UPLOADED");
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        acquisitionTaskService.updateStatus(file.getTaskId(), TaskStatus.UPLOADED);
        return toResponse(file);
    }

    @Override
    public DataFile getFile(Long fileId) {
        DataFile file = dataFileMapper.selectById(fileId);
        if (file == null) {
            throw new BizException("File not found: " + fileId);
        }
        return file;
    }

    @Override
    public List<DataFile> listFilesByTaskId(Long taskId) {
        acquisitionTaskService.getTask(taskId);
        return dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>()
                        .eq(DataFile::getTaskId, taskId)
                        .orderByDesc(DataFile::getCreatedAt)
        );
    }

    public DataFileResponse toResponse(DataFile file) {
        return new DataFileResponse(
                file.getId(),
                file.getTaskId(),
                file.getSessionId(),
                file.getFileRole(),
                file.getSourceKey(),
                file.getOriginalFilename(),
                file.getRelativePath(),
                file.getFileExt(),
                file.getContentType(),
                file.getFileSize(),
                file.getSha256(),
                file.getAssetType(),
                file.getStorageProvider(),
                file.getBucketName(),
                file.getObjectKey(),
                file.getStorageUrl(),
                file.getUploadStatus(),
                file.getCreatedAt()
        );
    }

    public QcReport getLatestReportByTaskId(Long taskId) {
        return qcReportMapper.selectOne(
                new LambdaQueryWrapper<QcReport>()
                        .eq(QcReport::getTaskId, taskId)
                        .orderByDesc(QcReport::getCreatedAt)
                        .last("limit 1")
        );
    }

    private List<ImportedBinary> collectBinaries(List<MultipartFile> files, MultipartFile archive) {
        List<ImportedBinary> binaries = new ArrayList<>();
        if (archive != null && !archive.isEmpty()) {
            List<ArchiveUtils.ExtractedEntry> entries = ArchiveUtils.extract(getBytes(archive));
            entries.forEach(entry -> binaries.add(new ImportedBinary(entry.relativePath(), entry.contentType(), entry.bytes())));
            return binaries;
        }
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    binaries.add(toImportedBinary(file));
                }
            }
        }
        return binaries;
    }

    private ImportedBinary toImportedBinary(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return new ImportedBinary(originalFilename, contentType, getBytes(file));
    }

    private CollectionSession resolveOrCreateSession(Long taskId, Long sessionId, AcquisitionTask task) {
        if (sessionId != null) {
            CollectionSession session = sessionMapper.selectById(sessionId);
            if (session == null || !session.getTaskId().equals(taskId)) {
                throw new BizException("Session not found or does not belong to task");
            }
            return session;
        }
        Subject subject = task.getSubjectId() != null ? subjectService.findById(task.getSubjectId()) : null;
        String subjectCode = subject != null ? subject.getSubjectCode() : "";
        CollectionSession session = new CollectionSession();
        session.setSessionCode(businessCodeGenerator.next("SESS"));
        session.setTaskId(taskId);
        session.setSubjectId(task.getSubjectId());
        session.setSessionId(UUID.randomUUID().toString());
        session.setSubjectCode(subjectCode);
        session.setSubjectCodeSnapshot(subjectCode);
        session.setActionName(task.getActionName());
        session.setProfileId(task.getProfileId());
        session.setStartedAt(LocalDateTime.now());
        session.setUploadStatus("RECEIVED");
        session.setSessionStatus("ACTIVE");
        session.setManifestJson("{}");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    private DataFile storeFile(AcquisitionTask task, CollectionSession session, ImportedBinary binary, AssetType assetType) {
        String objectKey = FileNameUtils.buildObjectKey(
                task.getTaskCode(),
                session.getSessionCode(),
                "MANUAL_UPLOAD",
                "manual",
                binary.originalFilename()
        );
        StoredFile storedFile = storageRouter.defaultService().upload(
                objectKey,
                binary.bytes(),
                binary.contentType(),
                binary.originalFilename()
        );

        DataFile dataFile = new DataFile();
        dataFile.setTaskId(task.getId());
        dataFile.setSessionId(session.getId());
        dataFile.setFileRole("MANUAL_UPLOAD");
        dataFile.setSourceKey("manual");
        dataFile.setOriginalFilename(binary.originalFilename());
        dataFile.setRelativePath(binary.originalFilename());
        dataFile.setFileExt(FileNameUtils.getExtension(binary.originalFilename()));
        dataFile.setContentType(binary.contentType());
        dataFile.setFileSize((long) binary.bytes().length);
        dataFile.setSha256(null);
        dataFile.setAssetType(assetType.name());
        dataFile.setStorageProvider(storedFile.storageProvider().name());
        dataFile.setBucketName(storedFile.bucketName());
        dataFile.setObjectKey(storedFile.objectKey());
        dataFile.setStorageUrl(storedFile.storageUrl());
        dataFile.setUploadStatus(FileUploadStatus.SUCCESS.name());
        dataFile.setCreatedAt(LocalDateTime.now());
        dataFileMapper.insert(dataFile);
        return dataFile;
    }

    private void runQc(Long taskId, Long sessionId, DataFile file, ImportedBinary binary, AssetType assetType) {
        try {
            QcExecutionResult qcResult = qcInspectionService.inspect(
                    binary.originalFilename(),
                    FileNameUtils.getExtension(binary.originalFilename()),
                    binary.contentType(),
                    binary.bytes(),
                    assetType
            );
            QcReport report = new QcReport();
            report.setTaskId(taskId);
            report.setSessionId(sessionId);
            report.setFileId(file.getId());
            report.setQcStatus(qcResult.status().name());
            report.setSummary(qcResult.summary());
            report.setReportJson(qcResult.reportJson());
            report.setCreatedAt(LocalDateTime.now());
            qcReportMapper.insert(report);
        } catch (Exception exception) {
            log.warn("QC failed for file {}: {}", binary.originalFilename(), exception.getMessage());
        }
    }

    private void markUploadCompleted(CollectionSession session, Long taskId) {
        session.setUploadStatus("UPLOADED");
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);
        acquisitionTaskService.updateStatus(taskId, TaskStatus.UPLOADED);
    }

    private AssetType inferAssetType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) return AssetType.SESSION_ARCHIVE_ZIP;
        if (lower.endsWith(".mp4")) return AssetType.RGB_VIDEO_MP4;
        if (lower.endsWith(".jsonl")) return AssetType.MOCAP_CSV;
        if (lower.endsWith(".csv")) return AssetType.MOCAP_CSV;
        return AssetType.OTHER;
    }

    private AssetType resolveAssetType(DataFile file) {
        if (file.getAssetType() != null && !file.getAssetType().isBlank()) {
            return AssetType.fromNullable(file.getAssetType());
        }
        return inferAssetType(file.getOriginalFilename());
    }

    private String buildRoleSessionName(Long fileId) {
        String base = "mmdp-direct-upload-" + fileId;
        return base.length() <= 64 ? base : base.substring(0, 64);
    }

    private String buildOssStorageUrl(String bucketName, String objectKey) {
        return "oss://" + bucketName + "/" + objectKey;
    }

    private byte[] getBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BizException("Failed to read uploaded file", exception);
        }
    }

    private TaskStatus toTaskStatus(QcStatus qcStatus) {
        return switch (qcStatus) {
            case PASSED -> TaskStatus.QC_PASSED;
            case WARNING -> TaskStatus.QC_WARNING;
            case FAILED -> TaskStatus.QC_FAILED;
        };
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException exception) {
            throw new BizException("Failed to parse QC report JSON", exception);
        }
    }

    private record ImportedBinary(String originalFilename, String contentType, byte[] bytes) {
    }
}
