package com.honortech.dataplatform.task.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class TaskListQueryRequest {

    private Long page = 1L;
    private Long pageSize = 20L;
    private String keyword;
    private Long taskId;
    private String taskCode;
    private String status;
    private String subjectCode;
    private String actionName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate collectDateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate collectDateTo;
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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public LocalDate getCollectDateFrom() {
        return collectDateFrom;
    }

    public void setCollectDateFrom(LocalDate collectDateFrom) {
        this.collectDateFrom = collectDateFrom;
    }

    public LocalDate getCollectDateTo() {
        return collectDateTo;
    }

    public void setCollectDateTo(LocalDate collectDateTo) {
        this.collectDateTo = collectDateTo;
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
