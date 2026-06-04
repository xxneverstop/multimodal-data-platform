package com.honortech.dataplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("collection_profile_source")
public class CollectionProfileSource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long profileId;
    private String sourceKey;
    private String sourceName;
    private String sourceType;
    private String deviceRoleCode;
    private Integer requiredFlag;
    private String filePattern;
    private String parsedAssetType;
    private String playbackKind;
    private Double expectedFps;
    private Double expectedSampleRate;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDeviceRoleCode() { return deviceRoleCode; }
    public void setDeviceRoleCode(String deviceRoleCode) { this.deviceRoleCode = deviceRoleCode; }
    public Integer getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(Integer requiredFlag) { this.requiredFlag = requiredFlag; }
    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }
    public String getParsedAssetType() { return parsedAssetType; }
    public void setParsedAssetType(String parsedAssetType) { this.parsedAssetType = parsedAssetType; }
    public String getPlaybackKind() { return playbackKind; }
    public void setPlaybackKind(String playbackKind) { this.playbackKind = playbackKind; }
    public Double getExpectedFps() { return expectedFps; }
    public void setExpectedFps(Double expectedFps) { this.expectedFps = expectedFps; }
    public Double getExpectedSampleRate() { return expectedSampleRate; }
    public void setExpectedSampleRate(Double expectedSampleRate) { this.expectedSampleRate = expectedSampleRate; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
