export interface DataFileResponse {
  id: number;
  taskId: number;
  originalFilename: string;
  fileExt: string;
  contentType: string;
  fileSize: number;
  bucketName: string;
  objectKey: string;
  storageUrl: string;
  uploadStatus: string;
  createdAt: string;
}

export interface FileUploadResponse {
  file: DataFileResponse;
  qcStatus: string;
  summary: string;
  reportJson: Record<string, unknown>;
}
