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
  "OTHER",
];
