import http from "@/api/http";
import type { DataFileResponse } from "@/types/file";

export async function fetchFile(fileId: number): Promise<DataFileResponse> {
  return http.get(`/api/files/${fileId}`);
}
