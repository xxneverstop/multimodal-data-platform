import type { DataAssetResponse } from "@/types/asset";
import type { ProcessingJobResponse } from "@/types/processing";
import type { QcReportResponse } from "@/types/qc";
import type { TaskResponse } from "@/types/task";

export interface SessionRecord {
  sessionId: string;
  taskId: number;
  taskName: string;
  sessionName: string;
  subjectCode: string;
  actionName: string;
  deviceSummary: string;
  modality: string;
  operatorName: string;
  taskStatus: string;
  dataStatus: string;
  processingStatus: string;
  qcStatus: string;
  assetCount: number;
  createdAt: string;
}

export interface AssetListItem {
  id: number;
  taskId: number;
  sessionId: string;
  taskName: string;
  assetName: string;
  fileName: string;
  fileSize: number | null;
  duration: string;
  uploader: string;
  uploadedAt: string;
  assetType: string;
  modality: string;
  fileFormat: string;
  sourceType: "upload" | "acquisition_sync" | "derived" | "external_register";
  annotationStatus: string;
  annotationTag: string;
  qcStatus: string;
  processingStatus: string;
  deliverableStatus: string;
  rawAsset: DataAssetResponse;
}

export interface AssetDetailViewModel {
  asset: AssetListItem;
  task: TaskResponse | null;
  session: SessionRecord | null;
  relatedAssets: AssetListItem[];
  jobs: ProcessingJobResponse[];
  reports: QcReportResponse[];
  metadata: Array<{ label: string; value: string }>;
}

export interface AcquisitionDetailViewModel {
  task: TaskResponse;
  sessions: SessionRecord[];
  assets: AssetListItem[];
  jobs: ProcessingJobResponse[];
  reports: QcReportResponse[];
}

export interface SessionDetailViewModel {
  session: SessionRecord;
  task: TaskResponse | null;
  assets: AssetListItem[];
  jobs: ProcessingJobResponse[];
  reports: QcReportResponse[];
}

export interface ProcessingTemplateRecord {
  id: string;
  name: string;
  templateType: string;
  inputModality: string;
  outputAssetType: string;
  scope: string;
  description: string;
  taskId: number | null;
  sessionId: string | null;
  recentRunAt: string | null;
  recentRunStatus: string | null;
}

export interface AnnotationTaskRecord {
  id: string;
  name: string;
  taskId: number;
  sessionId: string;
  assetName: string;
  annotationStatus: string;
  annotationTag: string;
  updatedAt: string;
  entryUrl: string;
}

export interface QcRuleRecord {
  id: string;
  name: string;
  templateName: string;
  scope: string;
  taskId: number | null;
  sessionId: string | null;
  status: string;
  updatedAt: string;
}

export interface QcLogRecord {
  id: number;
  taskId: number;
  sessionId: string;
  assetName: string;
  qcStatus: string;
  ruleTemplate: string;
  createdAt: string;
  summary: string;
  report: QcReportResponse;
}

export interface FinalAssetRecord {
  id: number;
  taskId: number;
  sessionId: string;
  assetName: string;
  assetType: string;
  modality: string;
  fileFormat: string;
  processingStatus: string;
  annotationStatus: string;
  qcStatus: string;
  deliverableStatus: string;
  updatedAt: string;
}

export interface OverviewDistributionItem {
  label: string;
  value: number;
  color: string;
}

export interface OverviewTrendSeries {
  label: string;
  color: string;
  values: number[];
}

export interface OverviewTrendChart {
  labels: string[];
  series: OverviewTrendSeries[];
}

export interface PlatformOverview {
  taskCount: number;
  sessionCount: number;
  assetCount: number;
  processingCount: number;
  qcPassRate: string;
  totalDuration: string;
  finalAssetCount: number;
  modalityDistribution: OverviewDistributionItem[];
  sourceDistribution: OverviewDistributionItem[];
  processingStatusDistribution: OverviewDistributionItem[];
  qcResultDistribution: OverviewDistributionItem[];
  assetGrowthTrend30d: OverviewTrendChart;
  sessionGrowthTrend30d: OverviewTrendChart;
  processingTrend30d: OverviewTrendChart;
  qcTrend30d: OverviewTrendChart;
  recentSessions: SessionRecord[];
  recentAssets: AssetListItem[];
  recentJobs: ProcessingJobResponse[];
}
