import type { AssetType } from "@/types/asset";

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
  pipelineId: string;
  status: string;
  parameters?: Record<string, unknown> | null;
  resultJson?: Record<string, unknown> | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProcessingJobRequest {
  pipelineId: string;
  parameters?: Record<string, unknown>;
}
