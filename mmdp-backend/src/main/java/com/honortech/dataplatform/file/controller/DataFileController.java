package com.honortech.dataplatform.file.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.util.MinioStorageClient;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.service.DataFileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@RestController
public class DataFileController {

    private final DataFileService dataFileService;
    private final MinioStorageClient minioStorageClient;

    public DataFileController(DataFileService dataFileService, MinioStorageClient minioStorageClient) {
        this.dataFileService = dataFileService;
        this.minioStorageClient = minioStorageClient;
    }

    @PostMapping("/api/tasks/{taskId}/files")
    public ApiResponse<FileUploadResponse> uploadTaskFile(
            @PathVariable Long taskId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "assetType", required = false) String multipartAssetType,
            @RequestParam(value = "assetType", required = false) String requestAssetType) {
        String assetType = multipartAssetType;
        if (assetType == null || assetType.isBlank()) {
            assetType = requestAssetType;
        }
        return ApiResponse.success("File uploaded", dataFileService.uploadTaskFile(taskId, file, assetType));
    }

    @GetMapping("/api/files/{fileId}")
    public ApiResponse<DataFileResponse> getFile(@PathVariable Long fileId) {
        DataFile file = dataFileService.getFile(fileId);
        return ApiResponse.success(new DataFileResponse(
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
        ));
    }

    @GetMapping("/api/tasks/{taskId}/files")
    public ApiResponse<List<DataFileResponse>> listTaskFiles(@PathVariable Long taskId) {
        return ApiResponse.success(
                dataFileService.listFilesByTaskId(taskId)
                        .stream()
                        .map(file -> new DataFileResponse(
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
                        ))
                        .toList()
        );
    }

    private static final Logger log = LoggerFactory.getLogger(DataFileController.class);

    @GetMapping("/api/files/{fileId}/download")
    public void downloadFile(@PathVariable Long fileId, HttpServletResponse response) {
        DataFile file = dataFileService.getFile(fileId);
        long size = minioStorageClient.getObjectSize(file.getObjectKey());
        response.setContentType(file.getContentType());
        response.setContentLengthLong(size);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getOriginalFilename() + "\"");
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

        try (InputStream in = minioStorageClient.download(file.getObjectKey());
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        } catch (Exception e) {
            if (isClientAbort(e)) {
                log.warn("Client disconnected during file stream: fileId={}, key={}", fileId, file.getObjectKey());
            } else {
                throw new RuntimeException("Failed to stream file", e);
            }
        }
    }

    private boolean isClientAbort(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String className = cause.getClass().getName();
            if (className.contains("ClientAbortException") || className.contains("EofException")) {
                return true;
            }
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Connection reset by peer") || msg.contains("Broken pipe"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
