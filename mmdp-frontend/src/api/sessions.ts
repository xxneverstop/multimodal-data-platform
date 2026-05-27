import http from "./http";

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
  taskId: number;
  subjectCode: string;
  actionName: string;
  startedAt: string;
  durationMs: number;
  sources: Record<string, PlaybackSource>;
}

export interface SessionResponse {
  id: number;
  taskId: number;
  sessionId: string;
  subjectCode: string;
  actionName: string;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  uploadStatus: string;
  createdAt: string;
  assets: any[];
}

export function fetchAllSessions(): Promise<SessionResponse[]> {
  return http.get("/api/sessions") as any;
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

export function importSession(taskId: number, formData: FormData): Promise<SessionResponse> {
  return http.post(`/api/tasks/${taskId}/sessions/import`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  }) as any;
}
