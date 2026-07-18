/** MotionDB 缺陷类型枚举 */
export type DefectType =
  | "jointJump"
  | "sliding"
  | "jointDeformity"
  | "floatPenetrate"
  | "displacementMissing"
  | "flatScene"
  | "temporalConsistency"
  | "testSet"
  | "other";

/** 帧级问题标注项，支持单帧和帧段 */
export interface FrameIssueItem {
  frame: number; // startFrame（单帧时即为帧号）
  endFrame?: number; // 帧段结束帧，无值表示单帧标注
  description: string;
  severity: "low" | "medium" | "high";
  category?: string; // 自由文本（向后兼容）
  defectType?: DefectType; // MotionDB 缺陷类型
}

export type AnnotationStatus = "UNANNOTATED" | "IN_PROGRESS" | "ANNOTATED";
export type QualityRating = "A" | "B" | "C" | "D";

/** MotionDB 风格缺陷评估 */
export interface MotiondbDefects {
  testSet: "Y" | "N";
  flatScene: "Y" | "N";
  jointJump: "0" | "1" | "2";
  jointDeformity: "Y" | "N";
  sliding: "0" | "1" | "2";
  floatPenetrate: "Y" | "N";
  displacementMissing: "Y" | "N";
  temporalConsistency: "fast" | "normal" | "slow";
}

/** 完整标注记录 */
export interface MotionAnnotation {
  id: number;
  assetId: number;
  status: AnnotationStatus;
  qualityRating: QualityRating | null;
  motionTags: string[];
  motiondbDefects: MotiondbDefects | null;
  frameIssues: FrameIssueItem[];
  textDescriptions: string[];
  overallComment: string | null;
  annotatorId: number | null;
  annotatorName: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** 保存标注请求 */
export interface MotionAnnotationRequest {
  status: AnnotationStatus;
  qualityRating: QualityRating | null;
  motionTags: string[];
  motiondbDefects: MotiondbDefects | null;
  frameIssues: FrameIssueItem[];
  textDescriptions: string[];
  overallComment: string | null;
  version: number;
}

/** 标注进度统计 */
export interface AnnotationProgressResponse {
  totalAssets: number;
  annotatedCount: number;
  inProgressCount: number;
  unannotatedCount: number;
  ratingDistribution: Record<string, number>;
}

// ── 常量 ──

export const MOTION_TAG_OPTIONS = [
  "walk", "run", "jump", "turn", "kick", "punch",
  "sit", "stand", "crouch", "dance", "wave", "other",
] as const;

export const SEVERITY_OPTIONS = [
  { value: "low" as const, label: "低", color: "#f59e0b" },
  { value: "medium" as const, label: "中", color: "#f97316" },
  { value: "high" as const, label: "高", color: "#ef4444" },
];

export const QUALITY_RATING_OPTIONS = [
  { value: "A" as const, label: "A", color: "#10b981" },
  { value: "B" as const, label: "B", color: "#0ea5e9" },
  { value: "C" as const, label: "C", color: "#f59e0b" },
  { value: "D" as const, label: "D", color: "#ef4444" },
];

/** MotionDB 缺陷字段定义 */
export const MOTIONDB_DEFECT_FIELDS: {
  key: keyof MotiondbDefects;
  label: string;
  type: "yn" | "three" | "speed";
}[] = [
  { key: "testSet", label: "加入测试集", type: "yn" },
  { key: "flatScene", label: "平地场景动作", type: "yn" },
  { key: "jointJump", label: "关节跳变", type: "three" },
  { key: "jointDeformity", label: "关节畸形", type: "yn" },
  { key: "sliding", label: "脚底滑动", type: "three" },
  { key: "floatPenetrate", label: "漂浮/穿透", type: "yn" },
  { key: "displacementMissing", label: "位移缺失", type: "yn" },
  { key: "temporalConsistency", label: "时空一致性", type: "speed" },
];

/** MotionDB 缺陷默认值 */
export const DEFAULT_MOTIONDB_DEFECTS: MotiondbDefects = {
  testSet: "N",
  flatScene: "N",
  jointJump: "0",
  jointDeformity: "N",
  sliding: "0",
  floatPenetrate: "N",
  displacementMissing: "N",
  temporalConsistency: "normal",
};

export const ANNOTATION_STATUS_LABELS: Record<AnnotationStatus, string> = {
  UNANNOTATED: "未标注",
  IN_PROGRESS: "标注中",
  ANNOTATED: "已标注",
};

export const ANNOTATION_STATUS_BADGE: Record<AnnotationStatus, { cls: string; color: string }> = {
  UNANNOTATED: { cls: "light2-badge-neutral", color: "#9298a3" },
  IN_PROGRESS: { cls: "light2-badge-info", color: "var(--color-brand-500)" },
  ANNOTATED: { cls: "light2-badge-ok", color: "#0d7d3e" },
};
