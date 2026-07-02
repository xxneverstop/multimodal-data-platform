package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.processing.dto.CreateManualProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateSessionJobRequest;
import com.honortech.dataplatform.processing.dto.ManualProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.WorkerClaimResponse;
import com.honortech.dataplatform.processing.dto.WorkerSuccessRequest;
import com.honortech.dataplatform.processing.dto.WorkerFailureRequest;

import java.util.List;

public interface ProcessingJobService {

    ProcessingJobResponse createJob(Long taskId, CreateProcessingJobRequest request);

    ManualProcessingJobResponse createManualJob(Long taskId, CreateManualProcessingJobRequest request);

    /** 对 Session 创建处理任务（绑定 sessionId） */
    ProcessingJobResponse createSessionJob(Long sessionId, CreateSessionJobRequest request);

    List<ProcessingJobResponse> listJobsByTaskId(Long taskId);

    /** 列出 Session 关联的处理任务 */
    List<ProcessingJobResponse> listJobsBySessionId(Long sessionId);

    ProcessingJobResponse getJob(Long jobId);

    /** 列出全部处理任务（按时间倒序，最近 50 条） */
    List<ProcessingJobResponse> listAllJobs();

    /** Worker 领取一个 PENDING 任务，返回含 inputFiles 的完整响应，无任务返回 null */
    WorkerClaimResponse claimJob();

    /** Worker 上报成功 */
    void completeJob(Long jobId, WorkerSuccessRequest request);

    /** Worker 上报失败 */
    void failJob(Long jobId, WorkerFailureRequest request);
}
