package com.honortech.dataplatform.file.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.service.DataFileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class DataFileController {

    private final DataFileService dataFileService;

    public DataFileController(DataFileService dataFileService) {
        this.dataFileService = dataFileService;
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
}
