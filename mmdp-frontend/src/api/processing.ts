import http from "@/api/http";
import type { AvailablePipelineResponse, CreateProcessingJobRequest, ProcessingJobResponse } from "@/types/processing";

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
