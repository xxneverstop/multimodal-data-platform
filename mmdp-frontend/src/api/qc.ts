import http from "@/api/http";
import type { QcReportResponse } from "@/types/qc";

export async function fetchTaskQcReports(taskId: number): Promise<QcReportResponse[]> {
  return http.get(`/api/tasks/${taskId}/qc-report`);
}
