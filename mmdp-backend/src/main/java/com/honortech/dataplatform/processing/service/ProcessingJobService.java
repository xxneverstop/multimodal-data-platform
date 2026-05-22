package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.processing.dto.CreateManualProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.ManualProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;

import java.util.List;

public interface ProcessingJobService {

    ProcessingJobResponse createJob(Long taskId, CreateProcessingJobRequest request);

    ManualProcessingJobResponse createManualJob(Long taskId, CreateManualProcessingJobRequest request);

    List<ProcessingJobResponse> listJobsByTaskId(Long taskId);

    ProcessingJobResponse getJob(Long jobId);
}
