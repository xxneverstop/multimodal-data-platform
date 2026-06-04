package com.honortech.dataplatform.session.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class SessionListQueryRequest {

    private Long page = 1L;
    private Long pageSize = 20L;
    private Long taskId;
    private String sessionId;
    private String sessionCode;
    private String qcStatus;
    private String uploadStatus;
    private String exportStatus;
    private String modality;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startedAtFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startedAtTo;
    private String sortBy;
    private String sortOrder;

    public Long getPage() {
        return page;
    }

    public void setPage(Long page) {
        this.page = page;
    }

    public Long getPageSize() {
        return pageSize;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(String qcStatus) {
        this.qcStatus = qcStatus;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getExportStatus() {
        return exportStatus;
    }

    public void setExportStatus(String exportStatus) {
        this.exportStatus = exportStatus;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public LocalDateTime getStartedAtFrom() {
        return startedAtFrom;
    }

    public void setStartedAtFrom(LocalDateTime startedAtFrom) {
        this.startedAtFrom = startedAtFrom;
    }

    public LocalDateTime getStartedAtTo() {
        return startedAtTo;
    }

    public void setStartedAtTo(LocalDateTime startedAtTo) {
        this.startedAtTo = startedAtTo;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
