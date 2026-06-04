import http from "@/api/http";
import type { CreateTaskRequest, TaskListQuery, TaskPageResponse, TaskResponse } from "@/types/task";
import type {
  DataFileResponse,
  FileUploadResponse,
  InitiateDirectUploadRequest,
  InitiateDirectUploadResponse,
} from "@/types/file";

export async function fetchTasks(current?: number | TaskListQuery, size = 10): Promise<TaskPageResponse> {
  if (typeof current === "object") {
    return http.get("/api/tasks", { params: current });
  }
  return http.get("/api/tasks", { params: { page: current ?? 1, pageSize: size } });
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

export async function uploadTaskFiles(
  taskId: number,
  files: File[],
  archive: File | null,
  sessionId?: number
): Promise<FileUploadResponse> {
  const formData = new FormData();
  if (archive) {
    formData.append("archive", archive);
  } else {
    files.forEach((f) => formData.append("files", f));
  }
  if (sessionId) {
    formData.append("sessionId", String(sessionId));
  }
  return http.post(`/api/tasks/${taskId}/files`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  });
}

export async function initiateTaskFileDirectUpload(
  taskId: number,
  payload: InitiateDirectUploadRequest,
  signal?: AbortSignal
): Promise<InitiateDirectUploadResponse> {
  return http.post(`/api/tasks/${taskId}/files/initiate`, payload, { timeout: 30_000, signal });
}

export async function completeTaskFileDirectUpload(
  fileId: number,
  signal?: AbortSignal
): Promise<DataFileResponse> {
  return http.post(`/api/files/${fileId}/complete`, { fileId }, { timeout: 60_000, signal });
}
