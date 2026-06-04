export interface DataFileResponse {
  id: number;
  taskId: number;
  sessionId: number | null;
  fileRole: string | null;
  sourceKey: string | null;
  originalFilename: string;
  relativePath: string | null;
  fileExt: string;
  contentType: string;
  fileSize: number;
  sha256: string | null;
  assetType: string | null;
  storageProvider: string;
  bucketName: string;
  objectKey: string;
  storageUrl: string;
  uploadStatus: string;
  createdAt: string;
}

export interface FileUploadResponse {
  file: DataFileResponse | null;
  files: DataFileResponse[] | null;
  fileCount: number;
  qcStatus: string | null;
  summary: string | null;
  reportJson: Record<string, unknown> | null;
  sessionId: number | null;
  sessionCode: string | null;
}

export interface InitiateDirectUploadRequest {
  fileName: string;
  fileSize: number;
  contentType: string;
  sessionId?: number;
  assetType?: string;
}

export interface InitiateDirectUploadResponse {
  fileId: number;
  sessionId: number;
  sessionCode: string;
  bucketName: string;
  region: string;
  endpoint: string | null;
  objectKey: string;
  stsAccessKeyId: string;
  stsAccessKeySecret: string;
  stsSecurityToken: string;
  expiration: string;
}
