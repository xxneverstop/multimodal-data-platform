package com.honortech.dataplatform.sessionimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("session_import_record")
public class SessionImportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long sessionRecordId;
    private Long collectorClientId;
    private String localTaskId;
    private String localSessionId;
    private Long archiveFileId;
    private String status;
    private String manifestJson;
    private String sourceEndpoint;
    private String requestId;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getSessionRecordId() {
        return sessionRecordId;
    }

    public void setSessionRecordId(Long sessionRecordId) {
        this.sessionRecordId = sessionRecordId;
    }

    public Long getCollectorClientId() {
        return collectorClientId;
    }

    public void setCollectorClientId(Long collectorClientId) {
        this.collectorClientId = collectorClientId;
    }

    public String getLocalTaskId() {
        return localTaskId;
    }

    public void setLocalTaskId(String localTaskId) {
        this.localTaskId = localTaskId;
    }

    public String getLocalSessionId() {
        return localSessionId;
    }

    public void setLocalSessionId(String localSessionId) {
        this.localSessionId = localSessionId;
    }

    public Long getArchiveFileId() {
        return archiveFileId;
    }

    public void setArchiveFileId(Long archiveFileId) {
        this.archiveFileId = archiveFileId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public String getSourceEndpoint() {
        return sourceEndpoint;
    }

    public void setSourceEndpoint(String sourceEndpoint) {
        this.sourceEndpoint = sourceEndpoint;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
