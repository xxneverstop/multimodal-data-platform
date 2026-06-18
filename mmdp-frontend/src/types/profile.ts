// ============================================================
// Profile 管理 — TypeScript 类型定义
// ============================================================

// --- 响应类型 ---

export interface CollectionProfileSourceResponse {
  id: number;
  sourceKey: string;
  sourceName: string;
  sourceType: string;
  deviceRoleCode?: string | null;
  required: boolean;
  filePattern?: string | null;
  parsedAssetType?: string | null;
  playbackKind?: string | null;
  expectedFps?: number | null;
  expectedSampleRate?: number | null;
  sortOrder: number;
}

export interface CollectionProfileResponse {
  id: number;
  profileCode: string;
  profileName: string;
  taskTypeCode: string;
  modalityGroupCode: string;
  deviceGroupCode: string;
  packageRuleCode: string;
  parserRuleCode: string;
  archiveRuleCode: string;
  playbackRuleCode: string;
  version: string;
  enabled: boolean;
  remark?: string | null;
  sources: CollectionProfileSourceResponse[];
}

// --- 请求类型 ---

export interface CreateSourceItem {
  sourceKey: string;
  sourceName: string;
  sourceType: string;
  deviceRoleCode?: string;
  requiredFlag?: boolean;
  filePattern?: string;
  parsedAssetType?: string;
  playbackKind?: string;
  expectedFps?: number | null;
  expectedSampleRate?: number | null;
  sortOrder?: number;
}

export interface CreateProfileRequest {
  profileCode: string;
  profileName: string;
  taskTypeCode: string;
  modalityGroupCode?: string;
  deviceGroupCode?: string;
  packageRuleCode?: string;
  parserRuleCode?: string;
  archiveRuleCode?: string;
  playbackRuleCode?: string;
  version?: string;
  remark?: string;
  sources?: CreateSourceItem[];
}

export interface UpdateProfileRequest {
  profileCode: string;
  profileName: string;
  taskTypeCode: string;
  modalityGroupCode?: string;
  deviceGroupCode?: string;
  packageRuleCode?: string;
  parserRuleCode?: string;
  archiveRuleCode?: string;
  playbackRuleCode?: string;
  version?: string;
  remark?: string;
}

export interface CreateProfileSourceRequest {
  sourceKey: string;
  sourceName: string;
  sourceType: string;
  deviceRoleCode?: string;
  requiredFlag?: boolean;
  filePattern?: string;
  parsedAssetType?: string;
  playbackKind?: string;
  expectedFps?: number | null;
  expectedSampleRate?: number | null;
  sortOrder?: number;
}

export interface UpdateProfileSourceRequest {
  sourceKey?: string;
  sourceName?: string;
  sourceType?: string;
  deviceRoleCode?: string;
  requiredFlag?: boolean;
  filePattern?: string;
  parsedAssetType?: string;
  playbackKind?: string;
  expectedFps?: number | null;
  expectedSampleRate?: number | null;
  sortOrder?: number;
}

// --- 下拉选项常量 ---

export const RULE_CODE_OPTIONS = [
  { value: "SESSION_ZIP_V1", label: "SESSION_ZIP_V1" },
  { value: "SESSION_JSONL_VIDEO_IMU_V1", label: "SESSION_JSONL_VIDEO_IMU_V1" },
  { value: "SESSION_ARCHIVE_V1", label: "SESSION_ARCHIVE_V1" },
  { value: "MULTI_VIDEO_IMU_V1", label: "MULTI_VIDEO_IMU_V1" },
];

export const TASK_TYPE_OPTIONS = [
  { value: "HUMAN_DEMO", label: "HUMAN_DEMO" },
];

export const SOURCE_TYPE_OPTIONS = [
  { value: "video", label: "video" },
  { value: "imu", label: "imu" },
  { value: "audio", label: "audio" },
  { value: "zed_svo2", label: "zed_svo2" },
  { value: "pose_csv", label: "pose_csv" },
  { value: "zed_mcap", label: "zed_mcap" },
];

export const DEVICE_ROLE_OPTIONS = [
  { value: "CAM_LEFT", label: "CAM_LEFT" },
  { value: "CAM_RIGHT", label: "CAM_RIGHT" },
  { value: "HMD", label: "HMD" },
  { value: "IMU", label: "IMU" },
  { value: "ZED", label: "ZED" },
];

export const PLAYBACK_KIND_OPTIONS = [
  { value: "video", label: "video" },
  { value: "imu_curve", label: "imu_curve" },
];
