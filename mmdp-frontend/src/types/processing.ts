import type { AssetType, DataAssetResponse } from "@/types/asset";

export interface AvailablePipelineResponse {
  pipelineId: string;
  displayName: string;
  description: string;
  readinessStatus: "READY" | "MISSING_REQUIRED_ASSETS";
  missingRequiredAssets: AssetType[];
  existingAssets: AssetType[];
  suggestedNextActions: string[];
}

export interface ProcessingJobResponse {
  id: number;
  taskId: number;
  taskCode?: string | null;
  sessionId?: number | null;
  sessionCode?: string | null;
  pipelineId: string;
  executorType: string;
  status: string;
  parameters?: Record<string, unknown> | null;
  paramsJson?: Record<string, unknown> | null;
  resultJson?: Record<string, unknown> | null;
  errorMessage?: string | null;
  operatorName?: string | null;
  toolName?: string | null;
  toolVersion?: string | null;
  logPath?: string | null;
  remark?: string | null;
  duration?: string | null;
  durationMs?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProcessingJobRequest {
  pipelineId: string;
  parameters?: Record<string, unknown>;
}

/** 对 Session 创建处理任务 */
export interface CreateSessionJobRequest {
  pipelineId: string;
  parameters?: Record<string, unknown>;
}

export interface ManualOutputAssetRequest {
  assetName: string;
  assetType: AssetType;
  sourceType: "UPLOADED_FILE" | "EXTERNAL_PATH";
  fileId?: number | null;
  externalPath?: string;
  description?: string;
}

export interface CreateManualProcessingJobRequest {
  pipelineId: string;
  inputAssetIds: number[];
  outputAssets: ManualOutputAssetRequest[];
  operatorName?: string;
  toolName?: string;
  toolVersion?: string;
  paramsJson?: Record<string, unknown> | null;
  logPath?: string;
  remark?: string;
}

export interface ManualProcessingJobResponse {
  job: ProcessingJobResponse;
  outputAssets: DataAssetResponse[];
}

export interface TaskLineageNode {
  id: string;
  type: "task" | "asset" | "job";
  label: string;
  assetType?: AssetType | null;
  pipelineId?: string | null;
  status?: string | null;
  detailId?: number | null;
}

export interface TaskLineageEdge {
  source: string;
  target: string;
  label: string;
}

export interface TaskLineageResponse {
  nodes: TaskLineageNode[];
  edges: TaskLineageEdge[];
}

/** 执行图（DAG）节点，类似 Airflow DAG 中的 Task */
export interface ExecutionGraphNode {
  id: string;
  type: "task" | "session" | "asset" | "job" | "qc";
  label: string;
  assetType?: string | null;
  pipelineId?: string | null;
  status?: string | null;
  detailId?: number | null;
  depth: number;
  sourceType?: string | null;
  executorType?: string | null;
}

/** 执行图（DAG）边 */
export interface ExecutionGraphEdge {
  source: string;
  target: string;
  label: string;
  style?: "solid" | "dashed";
}

/** 执行图响应 */
export interface ExecutionGraphResponse {
  nodes: ExecutionGraphNode[];
  edges: ExecutionGraphEdge[];
}

/** 布局后的节点（含坐标） */
export interface LayoutedNode extends ExecutionGraphNode {
  x: number;
  y: number;
  width: number;
  height: number;
}
