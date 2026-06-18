import http from "@/api/http";
import type {
  AvailablePipelineResponse,
  CreateManualProcessingJobRequest,
  CreateProcessingJobRequest,
  CreateSessionJobRequest,
  ManualProcessingJobResponse,
  ProcessingJobResponse,
  TaskLineageResponse
} from "@/types/processing";

export async function fetchAvailablePipelines(taskId: number): Promise<AvailablePipelineResponse[]> {
  return http.get(`/api/tasks/${taskId}/available-pipelines`);
}

export async function createProcessingJob(taskId: number, payload: CreateProcessingJobRequest): Promise<ProcessingJobResponse> {
  return http.post(`/api/tasks/${taskId}/processing-jobs`, payload);
}

export async function fetchTaskProcessingJobs(taskId: number): Promise<ProcessingJobResponse[]> {
  return http.get(`/api/tasks/${taskId}/processing-jobs`);
}

export async function fetchProcessingJob(jobId: number): Promise<ProcessingJobResponse> {
  return http.get(`/api/processing-jobs/${jobId}`);
}

export async function createManualProcessingJob(taskId: number, payload: CreateManualProcessingJobRequest): Promise<ManualProcessingJobResponse> {
  return http.post(`/api/tasks/${taskId}/processing-jobs/manual`, payload);
}

export async function fetchTaskLineage(taskId: number): Promise<TaskLineageResponse> {
  return http.get(`/api/tasks/${taskId}/lineage`);
}

/** 对 Session 创建处理任务 */
export async function createSessionJob(sessionId: number, payload: CreateSessionJobRequest): Promise<ProcessingJobResponse> {
  return http.post(`/api/sessions/${sessionId}/processing-jobs`, payload);
}

/** 列出 Session 关联的处理任务 */
export async function fetchSessionJobs(sessionId: number): Promise<ProcessingJobResponse[]> {
  return http.get(`/api/sessions/${sessionId}/processing-jobs`);
}
