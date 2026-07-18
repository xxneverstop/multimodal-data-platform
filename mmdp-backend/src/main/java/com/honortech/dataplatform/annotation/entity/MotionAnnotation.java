package com.honortech.dataplatform.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 动作标注实体，与 data_asset 一对一
 */
@TableName("motion_annotation")
public class MotionAnnotation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private String status;
    private String qualityRating;
    private String motionTags;
    private String frameIssues;
    private String motiondbDefects;
    private String textDescriptions;
    private String overallComment;
    private Long annotatorId;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQualityRating() {
        return qualityRating;
    }

    public void setQualityRating(String qualityRating) {
        this.qualityRating = qualityRating;
    }

    public String getMotionTags() {
        return motionTags;
    }

    public void setMotionTags(String motionTags) {
        this.motionTags = motionTags;
    }

    public String getFrameIssues() {
        return frameIssues;
    }

    public void setFrameIssues(String frameIssues) {
        this.frameIssues = frameIssues;
    }

    public String getMotiondbDefects() {
        return motiondbDefects;
    }

    public void setMotiondbDefects(String motiondbDefects) {
        this.motiondbDefects = motiondbDefects;
    }

    public String getTextDescriptions() {
        return textDescriptions;
    }

    public void setTextDescriptions(String textDescriptions) {
        this.textDescriptions = textDescriptions;
    }

    public String getOverallComment() {
        return overallComment;
    }

    public void setOverallComment(String overallComment) {
        this.overallComment = overallComment;
    }

    public Long getAnnotatorId() {
        return annotatorId;
    }

    public void setAnnotatorId(Long annotatorId) {
        this.annotatorId = annotatorId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
