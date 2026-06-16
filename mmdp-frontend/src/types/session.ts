import type { PageResponse } from "@/types/api";

export interface SessionListItem {
  id: number;
  sessionCode?: string | null;
  taskId: number;
  taskCode?: string | null;
  taskName: string;
  sessionId: string;
  subjectCode: string;
  actionName: string;
  profileName?: string | null;
  modality?: string | null;
  startedAt?: string | null;
  createdAt: string;
  collectorName?: string | null;
  uploadedAt?: string | null;
  uploadStatus: string;
  qcStatus: string;
  exportStatus: string;
  assetCount: number;
  fileCount: number;
  totalSize: number;
  sourceSummary: string;
}

export type SessionListPageResponse = PageResponse<SessionListItem>;

export interface SessionListQuery {
  page?: number;
  pageSize?: number;
  taskId?: number;
  sessionId?: string;
  sessionCode?: string;
  qcStatus?: string;
  uploadStatus?: string;
  exportStatus?: string;
  modality?: string;
  startedAtFrom?: string;
  startedAtTo?: string;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}
