package com.honortech.dataplatform.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.config.MinioProperties;
import com.honortech.dataplatform.common.enums.FileUploadStatus;
import com.honortech.dataplatform.common.enums.QcStatus;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.util.FileNameUtils;
import com.honortech.dataplatform.common.util.MinioStorageClient;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.qc.dto.QcExecutionResult;
import com.honortech.dataplatform.qc.entity.QcReport;
import com.honortech.dataplatform.qc.mapper.QcReportMapper;
import com.honortech.dataplatform.qc.service.QcInspectionService;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DataFileServiceImpl implements DataFileService {

    private final AcquisitionTaskService acquisitionTaskService;
    private final DataFileMapper dataFileMapper;
    private final QcReportMapper qcReportMapper;
    private final QcInspectionService qcInspectionService;
    private final MinioStorageClient minioStorageClient;
    private final MinioProperties minioProperties;
    private final ObjectMapper objectMapper;
    private final DataAssetService dataAssetService;

    public DataFileServiceImpl(
            AcquisitionTaskService acquisitionTaskService,
            DataFileMapper dataFileMapper,
            QcReportMapper qcReportMapper,
            QcInspectionService qcInspectionService,
            MinioStorageClient minioStorageClient,
            MinioProperties minioProperties,
            ObjectMapper objectMapper,
            DataAssetService dataAssetService) {
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataFileMapper = dataFileMapper;
        this.qcReportMapper = qcReportMapper;
        this.qcInspectionService = qcInspectionService;
        this.minioStorageClient = minioStorageClient;
        this.minioProperties = minioProperties;
        this.objectMapper = objectMapper;
        this.dataAssetService = dataAssetService;
    }

    @Override
    @Transactional
    public FileUploadResponse uploadTaskFile(Long taskId, MultipartFile file, String assetTypeValue) {
        acquisitionTaskService.getTask(taskId);
        if (file == null || file.isEmpty()) {
            throw new BizException("Uploaded file must not be empty");
        }

        AssetType assetType = AssetType.fromNullable(assetTypeValue);
        String originalFilename = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String fileExt = FileNameUtils.getExtension(originalFilename);
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        byte[] bytes = getBytes(file);
        String objectKey = FileNameUtils.buildObjectKey(taskId, originalFilename);

        String storageUrl = minioStorageClient.upload(objectKey, bytes, contentType);
        acquisitionTaskService.updateStatus(taskId, TaskStatus.UPLOADED);

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
        dataAssetService.createUploadedAsset(taskId, dataFile, assetType);

        QcExecutionResult qcResult = qcInspectionService.inspect(
                originalFilename,
                fileExt,
                contentType,
                bytes,
                assetType
        );

        QcReport report = new QcReport();
        report.setTaskId(taskId);
        report.setFileId(dataFile.getId());
        report.setQcStatus(qcResult.status().name());
        report.setSummary(qcResult.summary());
        report.setReportJson(qcResult.reportJson());
        report.setCreatedAt(LocalDateTime.now());
        qcReportMapper.insert(report);

        acquisitionTaskService.updateStatus(taskId, toTaskStatus(qcResult.status()));

        JsonNode reportJson = parseJson(qcResult.reportJson());
        return new FileUploadResponse(toResponse(dataFile), qcResult.status().name(), qcResult.summary(), reportJson);
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
                file.getOriginalFilename(),
                file.getFileExt(),
                file.getContentType(),
                file.getFileSize(),
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
}
