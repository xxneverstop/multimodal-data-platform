export type AssetSourceType = "UPLOADED_FILE" | "EXTERNAL_PATH" | "ACQUISITION_SYNC";

export type AssetType =
  | "RGB_SEQ_RAW"
  | "RGB_VIDEO_MP4"
  | "MOCAP_CSV"
  | "SMPL_RESULT"
  | "ALIGNED_RESULT"
  | "CAMERA_PARAM"
  | "SESSION_ARCHIVE_ZIP"
  | "OTHER";

export interface DataAssetResponse {
  id: number;
  taskId: number;
  sessionId?: number | null;
  sourceType: AssetSourceType;
  sourceKey?: string | null;
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
  objectKey?: string | null;
  storageUrl?: string | null;
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
