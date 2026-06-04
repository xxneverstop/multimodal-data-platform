package com.honortech.dataplatform.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("collection_session")
public class CollectionSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionCode;
    private Long taskId;
    private Long subjectId;
    private String sessionId;
    private String localTaskId;
    private String subjectCode;
    private String subjectCodeSnapshot;
    private String actionName;
    private String actionNameSnapshot;
    private Long profileId;
    private Long collectorClientId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private String timestampPolicy;
    private String manifestJson;
    private String uploadStatus;
    private String sessionStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getLocalTaskId() { return localTaskId; }
    public void setLocalTaskId(String localTaskId) { this.localTaskId = localTaskId; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectCodeSnapshot() { return subjectCodeSnapshot; }
    public void setSubjectCodeSnapshot(String subjectCodeSnapshot) { this.subjectCodeSnapshot = subjectCodeSnapshot; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public String getActionNameSnapshot() { return actionNameSnapshot; }
    public void setActionNameSnapshot(String actionNameSnapshot) { this.actionNameSnapshot = actionNameSnapshot; }

    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }

    public Long getCollectorClientId() { return collectorClientId; }
    public void setCollectorClientId(Long collectorClientId) { this.collectorClientId = collectorClientId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getTimestampPolicy() { return timestampPolicy; }
    public void setTimestampPolicy(String timestampPolicy) { this.timestampPolicy = timestampPolicy; }

    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }

    public String getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(String uploadStatus) { this.uploadStatus = uploadStatus; }

    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
