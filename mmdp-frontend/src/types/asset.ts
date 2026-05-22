export type AssetSourceType = "UPLOADED_FILE" | "EXTERNAL_PATH";

export type AssetType =
  | "RGB_SEQ_RAW"
  | "RGB_VIDEO_MP4"
  | "MOCAP_CSV"
  | "SMPL_RESULT"
  | "ALIGNED_RESULT"
  | "CAMERA_PARAM"
  | "OTHER";

export interface DataAssetResponse {
  id: number;
  taskId: number;
  sourceType: AssetSourceType;
  assetType: AssetType;
  displayName: string;
  fileId?: number | null;
  originalFilename?: string | null;
  fileExt?: string | null;
  contentType?: string | null;
  fileSize?: number | null;
  uploadStatus?: string | null;
  externalPath?: string | null;
  fileFormat?: string | null;
  sizeRemark?: string | null;
  description?: string | null;
  operatorRemark?: string | null;
  producedByJobId?: number | null;
  createdAt: string;
}

export interface CreateExternalAssetRequest {
  assetType: AssetType;
  displayName: string;
  externalPath: string;
  fileFormat?: string;
  sizeRemark?: string;
  description?: string;
  operatorRemark?: string;
}
