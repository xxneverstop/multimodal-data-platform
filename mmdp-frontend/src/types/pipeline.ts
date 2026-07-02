// ============================================================
// Pipeline / 处理规则 — TypeScript 类型定义
// ============================================================

import type { AssetType } from "./asset";

export interface PipelineDefinitionResponse {
  id: number;
  pipelineId: string;
  displayName: string;
  description?: string | null;
  inputAssetTypes: string[];
  outputAssetTypes: string[];
  executorType: string;
  enabled: number;
  profileIds: number[];
  createdAt: string;
  updatedAt: string;
}

export interface CreatePipelineRequest {
  pipelineId: string;
  displayName: string;
  description?: string;
  inputAssetTypes?: string[];
  outputAssetTypes?: string[];
  executorType: string;
  profileIds?: number[];
}

/** Worker 端注册的 Pipeline 元数据（来自 GET /api/pipelines/worker-available） */
export interface WorkerPipelineInfo {
  pipelineId: string;
  displayName: string;
  description?: string;
  version: string;
  inputAssetTypes: string[];
  outputAssetTypes: string[];
  runtimeDependencies?: string[];
}

// 所有支持的 AssetType 列表（用于多选）
export const ASSET_TYPE_OPTIONS: AssetType[] = [
  "RGB_SEQ_RAW",
  "RGB_VIDEO_MP4",
  "MOCAP_CSV",
  "SMPL_RESULT",
  "ALIGNED_RESULT",
  "CAMERA_PARAM",
  "LEFT_IMAGE_SEQUENCE",
  "RIGHT_IMAGE_SEQUENCE",
  "RAW_IMU_CSV",
  "FRAME_TIMESTAMPS_CSV",
  "DEPTH_RAW",
  "POSE_CACHE",
  "SESSION_ARCHIVE_ZIP",
  "IMU_ALIGNED_CSV",
  "ALIGNMENT_REPORT",
  "QC_SUMMARY",
  "OTHER",
];
