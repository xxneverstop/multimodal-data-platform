package com.honortech.dataplatform.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.common.enums.ProcessingJobStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.executor.PipelineExecutor;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ProcessingJobExecutor {

    private final Map<String, PipelineExecutor> executors;
    private final ProcessingJobMapper processingJobMapper;
    private final ObjectMapper objectMapper;

    public ProcessingJobExecutor(
            List<PipelineExecutor> executors,
            ProcessingJobMapper processingJobMapper,
            ObjectMapper objectMapper) {
        this.executors = executors.stream().collect(java.util.stream.Collectors.toMap(PipelineExecutor::getPipelineId, item -> item));
        this.processingJobMapper = processingJobMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProcessingJob execute(ProcessingJob job, List<DataAsset> assets) {
        PipelineExecutor executor = executors.get(job.getPipelineId());
        if (executor == null) {
            throw new BizException("Unsupported pipelineId: " + job.getPipelineId());
        }

        job.setStatus(ProcessingJobStatus.RUNNING.name());
        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);

        try {
            job.setResultJson(objectMapper.writeValueAsString(executor.execute(job, assets)));
            job.setStatus(ProcessingJobStatus.SUCCESS.name());
            job.setErrorMessage(null);
        } catch (JsonProcessingException exception) {
            job.setStatus(ProcessingJobStatus.FAILED.name());
            job.setErrorMessage("Failed to serialize processing result");
        } catch (Exception exception) {
            job.setStatus(ProcessingJobStatus.FAILED.name());
            job.setErrorMessage(exception.getMessage() == null ? "Pipeline execution failed" : exception.getMessage());
        }

        job.setUpdatedAt(LocalDateTime.now());
        processingJobMapper.updateById(job);
        return job;
    }
}
