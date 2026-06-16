import type { DataAssetResponse } from "@/types/asset";
import type { ProcessingJobResponse } from "@/types/processing";
import type { QcReportResponse } from "@/types/qc";
import type { TaskResponse } from "@/types/task";

export interface SessionRecord {
  id: number | null;
  sessionId: string;
  sessionCode?: string | null;
  taskId: number;
  taskCode?: string | null;
  taskName: string;
  sessionName: string;
  subjectCode: string;
  actionName: string;
  profileCode?: string | null;
  profileName?: string | null;
  deviceSummary: string;
  modality: string;
  operatorName: string;
  collectorName: string;
  taskStatus: string;
  dataStatus: string;
  processingStatus: string;
  qcStatus: string;
  uploadStatus: string;
  exportStatus: string;
  assetCount: number;
  fileCount: number;
  totalSize: number;
  createdAt: string;
  uploadedAt?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  durationMs?: number | null;
  sourceSummary: string;
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

export interface SessionDataGroup {
  key: string;
  title: string;
  sourceType: AssetListItem["sourceType"];
  assetTypes: string[];
  assetCount: number;
  fileCount: number;
  totalSize: number;
  assets: AssetListItem[];
}

export interface SessionQcResult {
  sessionId: string;
  taskId: number;
  overallStatus: string;
  fileResultCount: number;
  checkedAssetCount: number;
  note: string;
  fileResults: QcReportResponse[];
}

export interface QcRuleBinding {
  id: string;
  scopeType: "profile" | "session" | "upload";
  scopeLabel: string;
  enabled: boolean;
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
  groups: SessionDataGroup[];
  assets: AssetListItem[];
  jobs: ProcessingJobResponse[];
  reports: QcReportResponse[];
  qcSummary: SessionQcResult;
  metadata: Array<{ label: string; value: string }>;
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
  ruleType: "FILE" | "SESSION";
  appliesTo: string;
  profileCode?: string | null;
  sourceKey?: string | null;
  assetType?: string | null;
  enabled: boolean;
  priority: number;
  executionMode: "placeholder" | "active";
  bindings: QcRuleBinding[];
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
  sessionId: string;
  sessionCode?: string | null;
  taskId: number;
  taskName: string;
  subjectCode: string;
  actionName: string;
  assetCount: number;
  fileCount: number;
  totalSize: number;
  qcStatus: string;
  deliverableStatus: string;
  exportStatus: string;
  updatedAt: string;
  assets: AssetListItem[];
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
