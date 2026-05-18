import http from "@/api/http";
import type { CreateExternalAssetRequest, DataAssetResponse } from "@/types/asset";

export async function fetchTaskAssets(taskId: number): Promise<DataAssetResponse[]> {
  return http.get(`/api/tasks/${taskId}/assets`);
}

export async function createExternalAsset(taskId: number, payload: CreateExternalAssetRequest): Promise<DataAssetResponse> {
  return http.post(`/api/tasks/${taskId}/assets/external`, payload);
}
