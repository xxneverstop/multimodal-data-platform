import OSS from "ali-oss";
import { completeTaskFileDirectUpload, initiateTaskFileDirectUpload } from "@/api/tasks";
import type { AssetType } from "@/types/asset";
import type { DataFileResponse } from "@/types/file";

export interface DirectUploadOptions {
  taskId: number;
  file: File;
  assetType?: AssetType;
  sessionId?: number;
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

export interface DirectUploadResult {
  fileId: number;
  sessionId: number;
  sessionCode: string;
  file: DataFileResponse;
}

export class DirectUploadCompletionError extends Error {
  fileId: number;
  sessionId?: number;

  constructor(fileId: number, sessionId: number | undefined, message: string) {
    super(message);
    this.name = "DirectUploadCompletionError";
    this.fileId = fileId;
    this.sessionId = sessionId;
  }
}

export async function directUploadToOss(options: DirectUploadOptions): Promise<DirectUploadResult> {
  const signal = options.signal;

  // Check if already aborted before starting
  if (signal?.aborted) {
    throw new DOMException("Upload cancelled", "AbortError");
  }

  const initiation = await initiateTaskFileDirectUpload(options.taskId, {
    fileName: options.file.name,
    fileSize: options.file.size,
    contentType: options.file.type || "application/octet-stream",
    sessionId: options.sessionId,
    assetType: options.assetType,
  }, signal);

  // Check again after initiate (in case cancelled during the API call)
  if (signal?.aborted) {
    throw new DOMException("Upload cancelled", "AbortError");
  }

  const client = new OSS({
    region: initiation.region,
    accessKeyId: initiation.stsAccessKeyId,
    accessKeySecret: initiation.stsAccessKeySecret,
    stsToken: initiation.stsSecurityToken,
    bucket: initiation.bucketName,
    endpoint: initiation.endpoint ?? undefined,
    secure: true,
  });

  // Race OSS multipart upload against abort signal
  const uploadPromise = client.multipartUpload(initiation.objectKey, options.file, {
    partSize: 8 * 1024 * 1024,
    parallel: 4,
    progress: async (progress: number) => {
      if (signal?.aborted) return;
      options.onProgress?.(Math.max(0, Math.min(100, Math.round(progress * 100))));
    },
  });

  if (signal) {
    const abortPromise = new Promise<never>((_, reject) => {
      signal.addEventListener("abort", () => {
        reject(new DOMException("Upload cancelled", "AbortError"));
      }, { once: true });
    });
    await Promise.race([uploadPromise, abortPromise]);
  } else {
    await uploadPromise;
  }

  let file: DataFileResponse;
  try {
    file = await completeTaskFileDirectUpload(initiation.fileId, signal);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    const message = error instanceof Error ? error.message : "文件已上传到 OSS，但平台登记失败，可重试完成登记";
    throw new DirectUploadCompletionError(initiation.fileId, initiation.sessionId, message);
  }
  return {
    fileId: initiation.fileId,
    sessionId: initiation.sessionId,
    sessionCode: initiation.sessionCode,
    file,
  };
}

export async function retryDirectUploadCompletion(fileId: number): Promise<DataFileResponse> {
  return completeTaskFileDirectUpload(fileId);
}

export function inferAssetTypeForFile(fileName: string): AssetType {
  const lower = fileName.toLowerCase();
  if (lower.endsWith(".zip")) {
    return "SESSION_ARCHIVE_ZIP";
  }
  if (lower.endsWith(".mp4")) {
    return "RGB_VIDEO_MP4";
  }
  if (lower.endsWith(".csv") || lower.endsWith(".jsonl")) {
    return "MOCAP_CSV";
  }
  return "OTHER";
}
