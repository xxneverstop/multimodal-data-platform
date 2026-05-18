import http from "@/api/http";
import type { CreateTaskRequest, TaskPageResponse, TaskResponse } from "@/types/task";
import type { DataFileResponse, FileUploadResponse } from "@/types/file";

export async function fetchTasks(current = 1, size = 10): Promise<TaskPageResponse> {
  return http.get("/api/tasks", { params: { current, size } });
}

export async function fetchTask(taskId: number): Promise<TaskResponse> {
  return http.get(`/api/tasks/${taskId}`);
}

export async function createTask(payload: CreateTaskRequest): Promise<TaskResponse> {
  return http.post("/api/tasks", payload);
}

export async function fetchTaskFiles(taskId: number): Promise<DataFileResponse[]> {
  return http.get(`/api/tasks/${taskId}/files`);
}

export async function uploadTaskFile(taskId: number, file: File, assetType?: string): Promise<FileUploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  if (assetType) {
    formData.append("assetType", assetType);
  }
  return http.post(`/api/tasks/${taskId}/files`, formData, {
    headers: {
      "Content-Type": "multipart/form-data"
    }
  });
}
