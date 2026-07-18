import http from "@/api/http";
import type {
  MotionAnnotation,
  MotionAnnotationRequest,
  AnnotationProgressResponse,
} from "@/types/annotation";

/** 获取指定 Asset 的标注信息 */
export function fetchAnnotation(assetId: number): Promise<MotionAnnotation> {
  return http.get(`/api/annotation/assets/${assetId}`) as any;
}

/** 通过 fileId 查找标注（MotionViewer 使用） */
export function fetchAnnotationByFileId(
  fileId: number
): Promise<MotionAnnotation> {
  return http.get(`/api/annotation/assets/by-file/${fileId}`) as any;
}

/** 保存/更新标注 */
export function saveAnnotation(
  assetId: number,
  data: MotionAnnotationRequest
): Promise<MotionAnnotation> {
  return http.put(`/api/annotation/assets/${assetId}`, data) as any;
}

/** Session 标注进度统计 */
export function fetchSessionAnnotationProgress(
  sessionId: number
): Promise<AnnotationProgressResponse> {
  return http.get(
    `/api/annotation/sessions/${sessionId}/progress`
  ) as any;
}

/** Task 标注进度统计 */
export function fetchTaskAnnotationProgress(
  taskId: number
): Promise<AnnotationProgressResponse> {
  return http.get(`/api/annotation/tasks/${taskId}/progress`) as any;
}
