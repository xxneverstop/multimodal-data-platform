package com.honortech.dataplatform.file.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 文件上传响应。单文件兼容保留 file/qcStatus/summary/reportJson 字段；
 * 批量上传时 files 包含所有上传结果。
 */
public record FileUploadResponse(
        DataFileResponse file,
        List<DataFileResponse> files,
        int fileCount,
        String qcStatus,
        String summary,
        JsonNode reportJson,
        Long sessionId,
        String sessionCode
) {
    /** 单文件兼容构造 */
    public FileUploadResponse(DataFileResponse file, String qcStatus, String summary, JsonNode reportJson) {
        this(file, null, 1, qcStatus, summary, reportJson, null, null);
    }

    /** 多文件构造 */
    public FileUploadResponse(List<DataFileResponse> files, Long sessionId, String sessionCode) {
        this(files.isEmpty() ? null : files.getFirst(), files, files.size(),
                null, "Uploaded " + files.size() + " file(s)", null, sessionId, sessionCode);
    }
}
