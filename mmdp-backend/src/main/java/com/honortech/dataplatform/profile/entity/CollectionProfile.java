package com.honortech.dataplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("collection_profile")
public class CollectionProfile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String profileCode;
    private String profileName;
    private String taskTypeCode;
    private String modalityGroupCode;
    private String deviceGroupCode;
    private String packageRuleCode;
    private String parserRuleCode;
    private String archiveRuleCode;
    private String playbackRuleCode;
    private String version;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProfileCode() { return profileCode; }
    public void setProfileCode(String profileCode) { this.profileCode = profileCode; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getTaskTypeCode() { return taskTypeCode; }
    public void setTaskTypeCode(String taskTypeCode) { this.taskTypeCode = taskTypeCode; }
    public String getModalityGroupCode() { return modalityGroupCode; }
    public void setModalityGroupCode(String modalityGroupCode) { this.modalityGroupCode = modalityGroupCode; }
    public String getDeviceGroupCode() { return deviceGroupCode; }
    public void setDeviceGroupCode(String deviceGroupCode) { this.deviceGroupCode = deviceGroupCode; }
    public String getPackageRuleCode() { return packageRuleCode; }
    public void setPackageRuleCode(String packageRuleCode) { this.packageRuleCode = packageRuleCode; }
    public String getParserRuleCode() { return parserRuleCode; }
    public void setParserRuleCode(String parserRuleCode) { this.parserRuleCode = parserRuleCode; }
    public String getArchiveRuleCode() { return archiveRuleCode; }
    public void setArchiveRuleCode(String archiveRuleCode) { this.archiveRuleCode = archiveRuleCode; }
    public String getPlaybackRuleCode() { return playbackRuleCode; }
    public void setPlaybackRuleCode(String playbackRuleCode) { this.playbackRuleCode = playbackRuleCode; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
