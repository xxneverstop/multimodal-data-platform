import http from "@/api/http";
import type {
  PipelineDefinitionResponse,
  CreatePipelineRequest,
  WorkerPipelineInfo,
} from "@/types/pipeline";

export function fetchPipelines(): Promise<PipelineDefinitionResponse[]> {
  return http.get("/api/pipelines") as any;
}

export function fetchPipeline(id: number): Promise<PipelineDefinitionResponse> {
  return http.get(`/api/pipelines/${id}`) as any;
}

export function createPipeline(payload: CreatePipelineRequest): Promise<PipelineDefinitionResponse> {
  return http.post("/api/pipelines", payload) as any;
}

export function updatePipeline(id: number, payload: CreatePipelineRequest): Promise<PipelineDefinitionResponse> {
  return http.put(`/api/pipelines/${id}`, payload) as any;
}

export function disablePipeline(id: number): Promise<void> {
  return http.delete(`/api/pipelines/${id}`) as any;
}

/** 根据 session 获取可用的 pipeline 列表 */
export function fetchAvailablePipelines(sessionId: number): Promise<PipelineDefinitionResponse[]> {
  return http.get(`/api/pipelines/available/${sessionId}`) as any;
}

/** 获取 Worker 端已注册的 Pipeline 清单（供新建处理规则时下拉选择） */
export function fetchWorkerAvailablePipelines(): Promise<WorkerPipelineInfo[]> {
  return http.get("/api/pipelines/worker-available") as any;
}
