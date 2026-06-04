package com.honortech.dataplatform.file.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.storage.StorageProvider;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.file.dto.CompleteDirectUploadRequest;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadRequest;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.service.DataFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@RestController
public class DataFileController {

    private static final Logger log = LoggerFactory.getLogger(DataFileController.class);

    private final DataFileService dataFileService;
    private final StorageRouter storageRouter;

    public DataFileController(DataFileService dataFileService, StorageRouter storageRouter) {
        this.dataFileService = dataFileService;
        this.storageRouter = storageRouter;
    }

    @PostMapping("/api/tasks/{taskId}/files")
    public ApiResponse<FileUploadResponse> uploadTaskFiles(
            @PathVariable Long taskId,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "archive", required = false) MultipartFile archive,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestPart(value = "assetType", required = false) String multipartAssetType,
            @RequestParam(value = "assetType", required = false) String requestAssetType,
            HttpServletRequest request) {
        MultipartFile singleFile = getPartFile(request, "file");
        if (singleFile != null && !singleFile.isEmpty()
                && (files == null || files.isEmpty())
                && (archive == null || archive.isEmpty())) {
            String assetType = multipartAssetType != null && !multipartAssetType.isBlank()
                    ? multipartAssetType
                    : requestAssetType;
            return ApiResponse.success("File uploaded", dataFileService.uploadTaskFile(taskId, singleFile, assetType));
        }
        List<MultipartFile> effectiveFiles = files;
        if ((files == null || files.isEmpty()) && singleFile != null && !singleFile.isEmpty()) {
            effectiveFiles = List.of(singleFile);
        }
        FileUploadResponse response = dataFileService.uploadTaskFiles(taskId, sessionId, effectiveFiles, archive);
        return ApiResponse.success(response.fileCount() + " file(s) uploaded", response);
    }

    @PostMapping("/api/tasks/{taskId}/files/initiate")
    public ApiResponse<InitiateDirectUploadResponse> initiateDirectUpload(
            @PathVariable Long taskId,
            @Valid @RequestBody InitiateDirectUploadRequest request) {
        return ApiResponse.success("Direct upload initiated", dataFileService.initiateDirectUpload(taskId, request));
    }

    @PostMapping("/api/files/{fileId}/complete")
    public ApiResponse<DataFileResponse> completeDirectUpload(
            @PathVariable Long fileId,
            @Valid @RequestBody CompleteDirectUploadRequest request) {
        if (!fileId.equals(request.fileId())) {
            throw new IllegalArgumentException("Path fileId does not match request body fileId");
        }
        return ApiResponse.success("Direct upload completed", dataFileService.completeDirectUpload(request));
    }

    @GetMapping("/api/files/{fileId}")
    public ApiResponse<DataFileResponse> getFile(@PathVariable Long fileId) {
        DataFile file = dataFileService.getFile(fileId);
        return ApiResponse.success(toResponse(file));
    }

    @GetMapping("/api/tasks/{taskId}/files")
    public ApiResponse<List<DataFileResponse>> listTaskFiles(@PathVariable Long taskId) {
        return ApiResponse.success(dataFileService.listFilesByTaskId(taskId).stream().map(this::toResponse).toList());
    }

    @GetMapping("/api/files/{fileId}/download")
    public void downloadFile(@PathVariable Long fileId, HttpServletResponse response) {
        DataFile file = dataFileService.getFile(fileId);
        StorageProvider provider = StorageProvider.fromValue(file.getStorageProvider());
        long size = storageRouter.get(provider).getObjectSize(file.getBucketName(), file.getObjectKey());
        response.setContentType(file.getContentType());
        response.setContentLengthLong(size);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getOriginalFilename() + "\"");
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

        try (InputStream in = storageRouter.get(provider).download(file.getBucketName(), file.getObjectKey());
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

    private MultipartFile getPartFile(HttpServletRequest request, String name) {
        try {
            if (!(request instanceof org.springframework.web.multipart.MultipartHttpServletRequest mpRequest)) {
                return null;
            }
            return mpRequest.getFile(name);
        } catch (Exception exception) {
            return null;
        }
    }

    private DataFileResponse toResponse(DataFile file) {
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
