package com.honortech.dataplatform.file.controller;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.StorageProvider;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.file.dto.CompleteDirectUploadRequest;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadRequest;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.service.DataFileService;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
public class DataFileController {

    private static final Logger log = LoggerFactory.getLogger(DataFileController.class);
    private static final Pattern JOB_OBJECT_KEY_PATTERN = Pattern.compile("(?:^|/)jobs/(\\d+)(?:/|$)");

    private final DataFileService dataFileService;
    private final StorageRouter storageRouter;
    private final DataAssetService dataAssetService;
    private final ProcessingJobService processingJobService;

    public DataFileController(
            DataFileService dataFileService,
            StorageRouter storageRouter,
            DataAssetService dataAssetService,
            ProcessingJobService processingJobService) {
        this.dataFileService = dataFileService;
        this.storageRouter = storageRouter;
        this.dataAssetService = dataAssetService;
        this.processingJobService = processingJobService;
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

    @GetMapping("/api/sessions/{sessionId}/files")
    public ApiResponse<List<DataFileResponse>> listSessionFiles(@PathVariable Long sessionId) {
        return ApiResponse.success(dataFileService.listFilesBySessionId(sessionId).stream().map(this::toResponse).toList());
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

    @GetMapping("/api/sessions/{sessionId}/download")
    public void downloadSessionFiles(@PathVariable Long sessionId, HttpServletResponse response) {
        List<DataFile> files = dataFileService.listFilesBySessionId(sessionId);
        if (files.isEmpty()) {
            throw new BizException("该采集暂无可下载文件");
        }

        String archiveName = "session-" + sessionId + "-all-assets.zip";
        response.setContentType("application/zip");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(archiveName, StandardCharsets.UTF_8)
                        .build()
                        .toString()
        );

        Map<Long, DataAsset> assetsByFileId = indexAssetsByFileId(files);
        Map<Long, String> pipelinesByJobId = indexPipelinesByJobId(files);
        Set<String> usedEntryNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            // 视频、压缩包等数据通常已经压缩，关闭 ZIP 压缩可降低大批量下载时的 CPU 开销。
            zip.setLevel(Deflater.NO_COMPRESSION);
            for (DataFile file : files) {
                String entryName = buildZipEntryName(file, assetsByFileId, pipelinesByJobId, usedEntryNames);
                zip.putNextEntry(new ZipEntry(entryName));
                StorageProvider provider = StorageProvider.fromValue(file.getStorageProvider());
                try (InputStream in = storageRouter.get(provider).download(file.getBucketName(), file.getObjectKey())) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
        } catch (Exception e) {
            if (isClientAbort(e)) {
                log.warn("Client disconnected during session archive stream: sessionId={}", sessionId);
            } else {
                throw new BizException("批量下载采集文件失败", e);
            }
        }
    }

    private Map<Long, DataAsset> indexAssetsByFileId(List<DataFile> files) {
        Long taskId = resolveTaskId(files);
        if (taskId == null) {
            return Map.of();
        }

        Map<Long, DataAsset> result = new HashMap<>();
        for (DataAsset asset : dataAssetService.listByTaskId(taskId)) {
            if (asset.getFileId() == null) {
                continue;
            }
            DataAsset current = result.get(asset.getFileId());
            if (current == null || (current.getProducedByJobId() == null && asset.getProducedByJobId() != null)) {
                result.put(asset.getFileId(), asset);
            }
        }
        return result;
    }

    private Map<Long, String> indexPipelinesByJobId(List<DataFile> files) {
        Long taskId = resolveTaskId(files);
        if (taskId == null) {
            return Map.of();
        }

        Map<Long, String> result = new HashMap<>();
        for (ProcessingJobResponse job : processingJobService.listJobsByTaskId(taskId)) {
            result.put(job.id(), job.pipelineId());
        }
        return result;
    }

    private Long resolveTaskId(List<DataFile> files) {
        return files.stream()
                .map(DataFile::getTaskId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String buildZipEntryName(
            DataFile file,
            Map<Long, DataAsset> assetsByFileId,
            Map<Long, String> pipelinesByJobId,
            Set<String> usedEntryNames) {
        String directory = resolveArchiveDirectory(file, assetsByFileId.get(file.getId()), pipelinesByJobId);
        String path = sanitizeArchivePath(file.getRelativePath());
        if (path.isBlank()) {
            path = sanitizeArchivePath(file.getOriginalFilename());
        }
        if (path.isBlank()) {
            path = "file-" + file.getId();
        }

        String entryName = directory + "/" + path;
        if (usedEntryNames.add(entryName)) {
            return entryName;
        }

        String uniqueEntryName = directory + "/file-" + file.getId() + "-" + archiveFileName(path);
        usedEntryNames.add(uniqueEntryName);
        return uniqueEntryName;
    }

    private String resolveArchiveDirectory(
            DataFile file,
            DataAsset asset,
            Map<Long, String> pipelinesByJobId) {
        Long jobId = asset == null ? null : asset.getProducedByJobId();
        if (jobId == null && "PROCESSED_OUTPUT".equalsIgnoreCase(file.getFileRole())) {
            jobId = parseJobId(file.getObjectKey());
        }
        if (jobId == null) {
            return "PROCESSED_OUTPUT".equalsIgnoreCase(file.getFileRole())
                    ? "derived/unknown-processing"
                    : "raw";
        }

        String pipelineId = sanitizeArchiveSegment(pipelinesByJobId.get(jobId));
        if (pipelineId.isBlank()) {
            pipelineId = "unknown-processing";
        }
        return "derived/" + pipelineId + "/job-" + jobId;
    }

    private Long parseJobId(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        Matcher matcher = JOB_OBJECT_KEY_PATTERN.matcher(objectKey.replace('\\', '/'));
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String sanitizeArchiveSegment(String value) {
        return sanitizeArchivePath(value).replace('/', '_');
    }

    private String sanitizeArchivePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(value.replace('\\', '/').split("/"))
                .filter(segment -> !segment.isBlank() && !".".equals(segment) && !"..".equals(segment))
                .map(segment -> segment.replaceAll("[\\\\:*?\"<>|]", "_"))
                .filter(segment -> !segment.isBlank())
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private String archiveFileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator >= 0 ? path.substring(separator + 1) : path;
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
