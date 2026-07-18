import type { PageResponse } from "@/types/api";

export interface TaskResponse {
  id: number;
  taskCode?: string;
  taskName: string;
  subjectId?: number | null;
  subjectCode: string;
  subjectName?: string | null;
  actionName: string;
  profileId?: number | null;
  profileName?: string | null;
  deviceType: string;
  modality: string;
  collectDate: string;
  scene?: string;
  operatorName?: string;
  captureLocation?: string;
  status: string;
  remark?: string;
  sessionCount?: number;
  latestSessionStartedAt?: string | null;
  latestSessionStatus?: string | null;
  latestSessionId?: string | null;
  latestSessionCode?: string | null;
  createdAt: string;
  updatedAt: string;
}

export type TaskPageResponse = PageResponse<TaskResponse>;

export interface TaskListQuery {
  page?: number;
  pageSize?: number;
  keyword?: string;
  taskId?: number;
  taskCode?: string;
  status?: string;
  subjectCode?: string;
  actionName?: string;
  collectDateFrom?: string;
  collectDateTo?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export interface CreateTaskRequest {
  taskName: string;
  subjectCode?: string;
  subjectName?: string;
  actionName?: string;
  profileId: number | null;
  deviceType?: string;
  modality?: string;
  collectDate: string;
  scene?: string;
  operatorName?: string;
  captureLocation?: string;
  remark: string;
}
