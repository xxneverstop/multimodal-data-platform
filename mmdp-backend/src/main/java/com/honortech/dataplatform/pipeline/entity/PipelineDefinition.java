package com.honortech.dataplatform.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("pipeline_definition")
public class PipelineDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String pipelineId;
    private String displayName;
    private String description;
    private String inputAssetTypes;
    private String outputAssetTypes;
    private String executorType;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputAssetTypes() { return inputAssetTypes; }
    public void setInputAssetTypes(String inputAssetTypes) { this.inputAssetTypes = inputAssetTypes; }
    public String getOutputAssetTypes() { return outputAssetTypes; }
    public void setOutputAssetTypes(String outputAssetTypes) { this.outputAssetTypes = outputAssetTypes; }
    public String getExecutorType() { return executorType; }
    public void setExecutorType(String executorType) { this.executorType = executorType; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
