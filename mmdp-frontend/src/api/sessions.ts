import http from "./http";
import type { SessionListPageResponse, SessionListQuery } from "@/types/session";
import type {
  InitiateImportUploadRequest,
  InitiateImportUploadResponse,
} from "@/types/file";

export interface PlaybackSource {
  type: string;
  label: string;
  videoUrl: string | null;
  fps: number | null;
  sampleCount: number | null;
  jsonlUrl: string | null;
  sampleRate: number | null;
}

export interface SessionPlaybackResponse {
  sessionId: string;
  sessionCode?: string | null;
  taskId: number;
  subjectCode: string;
  actionName: string;
  profileCode?: string | null;
  timestampPolicy?: string | null;
  startedAt: string;
  durationMs: number;
  sources: Record<string, PlaybackSource>;
}

export interface SessionResponse {
  id: number;
  sessionCode?: string | null;
  taskId: number;
  taskName: string;
  sessionId: string;
  localSessionId?: string | null;
  subjectCode: string;
  actionName: string;
  profileId?: number | null;
  profileCode?: string | null;
  profileName?: string | null;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  uploadStatus: string;
  sessionStatus?: string | null;
  collectorName?: string | null;
  uploadedAt?: string | null;
  createdAt: string;
  assets: any[];
}

export interface SessionImportResponse {
  importId: number | null;
  platformTaskId: number;
  platformSessionId: number;
  status: string;
  existing: boolean;
}

export interface FinalizeSessionImportUploadedFile {
  originalFilename: string;
  relativePath: string;
  objectKey: string;
  contentType: string;
  fileSize: number;
  sha256?: string | null;
}

export interface FinalizeSessionImportRequest {
  taskId: number;
  importKey: string;
  requestId?: string;
  manifest: Record<string, unknown>;
  uploadedFiles: FinalizeSessionImportUploadedFile[];
}

export interface FinalizeSessionImportResponse {
  importId: number | null;
  platformTaskId: number;
  platformSessionId: number | null;
  platformSessionCode: string | null;
  localSessionId: string | null;
  profileCode: string | null;
  subjectCode: string | null;
  status: string;
  existing: boolean;
  createdFileCount: number;
  createdAssetCount: number;
  sourceCount: number;
}

export function fetchSessions(query: SessionListQuery = {}): Promise<SessionListPageResponse> {
  return http.get("/api/sessions", { params: query }) as any;
}

export async function fetchAllSessions(): Promise<SessionResponse[]> {
  const page = await fetchSessions({ page: 1, pageSize: 500 });
  return page.records.map((item) => ({
    id: item.id,
    sessionCode: item.sessionCode ?? null,
    taskId: item.taskId,
    taskName: item.taskName,
    sessionId: item.sessionId,
    localSessionId: item.sessionId,
    subjectCode: item.subjectCode,
    actionName: item.actionName,
    profileId: null,
    profileCode: null,
    profileName: item.profileName ?? null,
    startedAt: item.startedAt ?? item.createdAt,
    endedAt: null,
    durationMs: null,
    uploadStatus: item.uploadStatus,
    sessionStatus: item.qcStatus,
    collectorName: item.collectorName ?? null,
    uploadedAt: item.uploadedAt ?? null,
    createdAt: item.createdAt,
    assets: [],
  }));
}

export function fetchTaskSessions(taskId: number): Promise<SessionResponse[]> {
  return http.get(`/api/tasks/${taskId}/sessions`) as any;
}

export function fetchSessionDetail(sessionId: string): Promise<SessionResponse> {
  return http.get(`/api/sessions/${encodeURIComponent(sessionId)}`) as any;
}

export function fetchSessionPlayback(sessionId: string): Promise<SessionPlaybackResponse> {
  return http.get(`/api/sessions/${encodeURIComponent(sessionId)}/playback`) as any;
}

export function importSessionPackage(formData: FormData): Promise<SessionImportResponse> {
  return http.post("/api/session-imports", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  }) as any;
}

export function importSession(taskId: number, formData: FormData): Promise<SessionImportResponse> {
  return http.post(`/api/tasks/${taskId}/sessions/import`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  }) as any;
}

export function initiateSessionImportUpload(
  taskId: number,
  payload: InitiateImportUploadRequest,
  signal?: AbortSignal,
): Promise<InitiateImportUploadResponse> {
  return http.post(`/api/tasks/${taskId}/session-imports/uploads/initiate`, payload, {
    timeout: 30_000,
    signal,
  }) as any;
}

export function finalizeSessionImport(
  payload: FinalizeSessionImportRequest,
  signal?: AbortSignal,
): Promise<FinalizeSessionImportResponse> {
  return http.post("/api/session-imports/finalize", payload, {
    timeout: 120_000,
    signal,
  }) as any;
}
