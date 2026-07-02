import http from "@/api/http";
import type { CleanupResult } from "@/types/admin";

export async function deleteJobOutputs(jobId: number): Promise<CleanupResult> {
  return http.delete(`/api/admin/processing-jobs/${jobId}/outputs`);
}

export async function deleteSession(sessionId: number): Promise<CleanupResult> {
  return http.delete(`/api/admin/sessions/${sessionId}`);
}

export async function deleteTask(taskId: number): Promise<CleanupResult> {
  return http.delete(`/api/admin/tasks/${taskId}`);
}
