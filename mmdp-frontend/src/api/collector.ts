import axios from "axios";

const collectorHttp = axios.create({
  baseURL: "http://localhost:19022",
  timeout: 10000
});

export interface CollectorDevice {
  id: string;
  type: string;
  name: string;
  spec: string;
  available: boolean;
}

export interface StartSessionParams {
  taskId: string;
  taskName?: string;
  subjectCode: string;
  subjectName?: string;
  actionName: string;
  profileCode?: string;
  profileName?: string;
}

// ── Source status types ─────────────────────────────────────────────────

export interface VideoSourceStatus {
  type: "video";
  status: "running" | "stopped" | "missing";
  frameIndex: number;
  fps: number;
  videoTimeMs: number;
  hostReceiveTimestamp: number;
}

export interface ImuSample {
  acc: { x: number; y: number; z: number };
  gyro: { x: number; y: number; z: number };
  quat: { w: number; x: number; y: number; z: number };
}

export interface ImuSourceStatus {
  type: "imu";
  status: "running" | "stopped" | "missing";
  sampleIndex: number;
  sampleRate: number;
  latest: ImuSample;
  hostReceiveTimestamp: number;
}

export type SourceStatus = VideoSourceStatus | ImuSourceStatus;

export interface RealtimeStatus {
  type: "realtime_status";
  sessionId: string | null;
  taskId?: string;
  subjectCode?: string;
  actionName?: string;
  running: boolean;
  elapsedMs: number;
  sessionPhase?: string;
  pendingAction?: string;
  sources: {
    left: VideoSourceStatus;
    right: VideoSourceStatus;
    hmd: VideoSourceStatus;
    imu: ImuSourceStatus;
  };
  logs: string[];
}

// ── API functions ───────────────────────────────────────────────────────

export async function fetchHealth() {
  const { data } = await collectorHttp.get("/health");
  return data;
}

export async function fetchDevices(): Promise<CollectorDevice[]> {
  const { data } = await collectorHttp.get("/devices");
  return data.devices;
}

export async function startSession(params: StartSessionParams) {
  const { data } = await collectorHttp.post("/session/start", params);
  return data;
}

export async function stopSession() {
  const { data } = await collectorHttp.post("/session/stop");
  return data;
}

export async function saveSession() {
  const { data } = await collectorHttp.post("/session/save");
  return data;
}

export async function discardSession() {
  const { data } = await collectorHttp.post("/session/discard");
  return data;
}

export async function fetchCurrentSession(): Promise<RealtimeStatus> {
  const { data } = await collectorHttp.get("/session/current");
  return data;
}

export function createRealtimeSocket(): WebSocket {
  return new WebSocket("ws://localhost:19022/ws/realtime");
}

/** Build the MJPEG stream URL for a video source. Appends a cache-busting
 *  query param so the browser reconnects on session restart. */
export function streamUrl(source: "left" | "right" | "hmd"): string {
  return `http://localhost:19022/stream/${source}?t=${Date.now()}`;
}

export interface UploadParams {
  platformUrl: string;
  sessionId?: string;
  platformTaskId?: number;
}

export interface UploadResult {
  success: boolean;
  message: string;
  platformResponse?: any;
}

export async function uploadSession(params: UploadParams): Promise<UploadResult> {
  const { data } = await collectorHttp.post("/session/upload", params, { timeout: 120000 });
  return data;
}

export async function fetchSessionHistory(): Promise<{ sessions: any[] }> {
  const { data } = await collectorHttp.get("/session/history");
  return data;
}
