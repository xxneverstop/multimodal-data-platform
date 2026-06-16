import { fetchTaskAssets } from "@/api/assets";
import { fetchTaskQcReports } from "@/api/qc";
import { fetchTaskProcessingJobs } from "@/api/processing";
import { fetchAllSessions, type SessionResponse } from "@/api/sessions";
import { fetchTask, fetchTasks } from "@/api/tasks";
import type { DataAssetResponse } from "@/types/asset";
import type { ProcessingJobResponse } from "@/types/processing";
import type {
  AcquisitionDetailViewModel,
  AnnotationTaskRecord,
  AssetDetailViewModel,
  AssetListItem,
  FinalAssetRecord,
  OverviewDistributionItem,
  OverviewTrendChart,
  PlatformOverview,
  ProcessingTemplateRecord,
  QcLogRecord,
  QcRuleBinding,
  QcRuleRecord,
  SessionDataGroup,
  SessionDetailViewModel,
  SessionQcResult,
  SessionRecord,
} from "@/types/platform";
import type { QcReportResponse } from "@/types/qc";
import type { TaskPageResponse, TaskResponse } from "@/types/task";

type TaskWorkspace = {
  task: TaskResponse;
  sessions: SessionResponse[];
  assets: DataAssetResponse[];
  jobs: ProcessingJobResponse[];
  reports: QcReportResponse[];
};

const ANNOTATION_STATUSES = ["待标注", "已标注", "待复核"];
const ANNOTATION_TAGS = ["有效", "无效", "异常动作", "时间漂移"];

const DONUT_COLORS = {
  blue: "#2563eb",
  sky: "#0ea5e9",
  cyan: "#14b8a6",
  emerald: "#10b981",
  amber: "#f59e0b",
  orange: "#f97316",
  rose: "#ef4444",
  slate: "#94a3b8",
};

async function safe<T>(loader: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await loader();
  } catch {
    return fallback;
  }
}

function buildSyntheticSessionId(taskId: number) {
  return `TASK-${taskId}-MANUAL`;
}

function buildSyntheticSessionCode(taskId: number) {
  return `MANUAL-${String(taskId).padStart(4, "0")}`;
}

function isPassedQc(status?: string | null) {
  return status === "PASSED" || status === "QC_PASSED";
}

function isFailedQc(status?: string | null) {
  return status === "FAILED" || status === "QC_FAILED";
}

function parseDurationSeconds(value?: string) {
  if (!value || !value.includes(":")) {
    return 0;
  }
  const parts = value.split(":").map((item) => Number(item));
  if (parts.some((item) => Number.isNaN(item))) {
    return 0;
  }
  if (parts.length === 3) {
    return parts[0] * 3600 + parts[1] * 60 + parts[2];
  }
  if (parts.length === 2) {
    return parts[0] * 60 + parts[1];
  }
  return 0;
}

function formatTotalDuration(totalSeconds: number) {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  return `${minutes}m`;
}

function inferDuration(asset: DataAssetResponse) {
  const assetType = asset.assetType.toUpperCase();
  const fileFormat = String(asset.fileFormat || asset.fileExt || "").toLowerCase();
  if (assetType.includes("VIDEO") || fileFormat === "mp4") {
    return "00:05:20";
  }
  if (assetType.includes("IMU") || assetType.includes("MOCAP")) {
    return "00:03:40";
  }
  if (assetType.includes("SMPL")) {
    return "00:02:45";
  }
  return "00:01:15";
}

function inferAnnotationStatus(assetId: number) {
  return ANNOTATION_STATUSES[assetId % ANNOTATION_STATUSES.length];
}

function inferAnnotationTag(assetId: number) {
  return ANNOTATION_TAGS[assetId % ANNOTATION_TAGS.length];
}

function deriveProcessingStatus(jobs: ProcessingJobResponse[]) {
  const latestJob = [...jobs].sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0];
  return latestJob?.status ?? "PENDING";
}

function deriveDataStatus(assets: DataAssetResponse[]) {
  if (!assets.length) {
    return "PENDING";
  }
  if (assets.some((asset) => asset.producedByJobId)) {
    return "READY";
  }
  return "UPLOADED";
}

function deriveQcStatus(taskStatus: string, reports: QcReportResponse[]) {
  if (!reports.length) {
    return taskStatus || "PENDING";
  }
  const latest = [...reports].sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0];
  return latest?.qcStatus ?? taskStatus ?? "PENDING";
}

function mapSourceType(asset: DataAssetResponse): AssetListItem["sourceType"] {
  if (asset.producedByJobId) {
    return "derived";
  }
  if (asset.sourceType === "EXTERNAL_PATH") {
    return "external_register";
  }
  if (asset.sourceType === "ACQUISITION_SYNC") {
    return "acquisition_sync";
  }
  return "upload";
}

function inferModalityCategory(asset: DataAssetResponse) {
  const assetType = asset.assetType.toUpperCase();
  const fileFormat = String(asset.fileFormat || asset.fileExt || "").toLowerCase();
  if (assetType.includes("SMPL")) {
    return "smpl";
  }
  if (assetType.includes("IMU") || assetType.includes("MOCAP")) {
    return "imu";
  }
  if (fileFormat === "csv" || assetType.includes("CSV")) {
    return "csv";
  }
  if (fileFormat === "hdf5" || fileFormat === "h5") {
    return "hdf5";
  }
  return "video";
}

function inferModalityLabel(asset: DataAssetResponse, task: TaskResponse) {
  switch (inferModalityCategory(asset)) {
    case "smpl":
      return "SMPL";
    case "imu":
      return "IMU";
    case "csv":
      return "CSV";
    case "hdf5":
      return "HDF5";
    default:
      return task.modality || "视频";
  }
}

function inferDeliverableStatus(item: Pick<AssetListItem, "qcStatus" | "annotationStatus" | "sourceType">) {
  if (isPassedQc(item.qcStatus) && (item.annotationStatus === "已标注" || item.sourceType === "derived")) {
    return "READY";
  }
  return "PENDING";
}

function summarizeSourceTypes(assets: DataAssetResponse[]) {
  if (!assets.length) {
    return "暂无数据";
  }
  const counts = new Map<string, number>();
  for (const asset of assets) {
    const key = asset.sourceKey || asset.assetType;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .slice(0, 3)
    .map(([label, count]) => `${label} x${count}`)
    .join(" / ");
}

function createAssetListItem(task: TaskResponse, asset: DataAssetResponse, sessionLabel: string, reports: QcReportResponse[]): AssetListItem {
  const qcReport = reports.find((report) => report.fileId === asset.fileId);
  const item: AssetListItem = {
    id: asset.id,
    taskId: task.id,
    sessionId: sessionLabel,
    taskName: task.taskName,
    assetName: asset.displayName,
    fileName: asset.originalFilename || asset.externalPath || asset.displayName,
    fileSize: asset.fileSize ?? null,
    duration: inferDuration(asset),
    uploader: task.operatorName || "-",
    uploadedAt: asset.createdAt,
    assetType: asset.assetType,
    modality: inferModalityLabel(asset, task),
    fileFormat: asset.fileFormat || asset.fileExt || "-",
    sourceType: mapSourceType(asset),
    annotationStatus: inferAnnotationStatus(asset.id),
    annotationTag: inferAnnotationTag(asset.id),
    qcStatus: qcReport?.qcStatus || asset.uploadStatus || "PENDING",
    processingStatus: asset.producedByJobId ? "SUCCESS" : "PENDING",
    deliverableStatus: "PENDING",
    rawAsset: asset,
  };
  item.deliverableStatus = inferDeliverableStatus(item);
  return item;
}

function buildSessionQcSummary(sessionId: string, taskId: number, reports: QcReportResponse[], checkedAssetCount: number): SessionQcResult {
  let overallStatus = "PENDING";
  if (reports.some((report) => isFailedQc(report.qcStatus))) {
    overallStatus = "QC_FAILED";
  } else if (reports.some((report) => report.qcStatus === "QC_WARNING" || report.qcStatus === "WARNING")) {
    overallStatus = "QC_WARNING";
  } else if (reports.some((report) => isPassedQc(report.qcStatus))) {
    overallStatus = "QC_PASSED";
  }

  const note = reports.length
    ? "当前仅展示文件级质检结果，Session 级规则执行待后端接入。"
    : "当前没有可用的质检结果。";

  return {
    sessionId,
    taskId,
    overallStatus,
    fileResultCount: reports.length,
    checkedAssetCount,
    note,
    fileResults: reports,
  };
}

function buildSessionDataGroups(assets: AssetListItem[]): SessionDataGroup[] {
  const groups = new Map<string, SessionDataGroup>();
  for (const asset of assets) {
    const key = asset.rawAsset.sourceKey || `${asset.sourceType}:${asset.assetType}`;
    const title = asset.rawAsset.sourceKey || asset.assetType;
    const existing = groups.get(key) ?? {
      key,
      title,
      sourceType: asset.sourceType,
      assetTypes: [],
      assetCount: 0,
      fileCount: 0,
      totalSize: 0,
      assets: [],
    };
    existing.assetCount += 1;
    existing.fileCount += asset.rawAsset.fileId ? 1 : 0;
    existing.totalSize += asset.fileSize ?? 0;
    existing.assets.push(asset);
    if (!existing.assetTypes.includes(asset.assetType)) {
      existing.assetTypes.push(asset.assetType);
    }
    groups.set(key, existing);
  }
  return Array.from(groups.values()).sort((left, right) => left.title.localeCompare(right.title));
}

function createRealSessionRecord(
  session: SessionResponse,
  task: TaskResponse,
  sessionAssets: DataAssetResponse[],
  sessionJobs: ProcessingJobResponse[],
  sessionReports: QcReportResponse[],
): SessionRecord {
  return {
    id: session.id,
    sessionId: session.sessionId,
    sessionCode: session.sessionCode ?? null,
    taskId: task.id,
    taskCode: task.taskCode ?? null,
    taskName: task.taskName,
    sessionName: session.sessionCode || session.sessionId,
    subjectCode: session.subjectCode || task.subjectCode,
    actionName: session.actionName || task.actionName,
    profileCode: session.profileCode ?? null,
    profileName: session.profileName ?? task.profileName ?? null,
    deviceSummary: task.deviceType,
    modality: task.modality,
    operatorName: task.operatorName || "-",
    collectorName: session.collectorName || "-",
    taskStatus: task.status,
    dataStatus: deriveDataStatus(sessionAssets),
    processingStatus: deriveProcessingStatus(sessionJobs),
    qcStatus: deriveQcStatus(task.status, sessionReports),
    uploadStatus: session.uploadStatus,
    exportStatus: sessionAssets.some((asset) => asset.storageUrl) ? "READY" : "PENDING",
    assetCount: sessionAssets.length,
    fileCount: sessionAssets.filter((asset) => asset.fileId).length,
    totalSize: sessionAssets.reduce((sum, asset) => sum + (asset.fileSize ?? 0), 0),
    createdAt: session.createdAt,
    uploadedAt: session.uploadedAt ?? null,
    startedAt: session.startedAt,
    endedAt: session.endedAt,
    durationMs: session.durationMs,
    sourceSummary: summarizeSourceTypes(sessionAssets),
  };
}

function createSyntheticSessionRecord(
  task: TaskResponse,
  assets: DataAssetResponse[],
  jobs: ProcessingJobResponse[],
  reports: QcReportResponse[],
): SessionRecord {
  return {
    id: null,
    sessionId: buildSyntheticSessionId(task.id),
    sessionCode: buildSyntheticSessionCode(task.id),
    taskId: task.id,
    taskCode: task.taskCode ?? null,
    taskName: task.taskName,
    sessionName: "手工上传批次",
    subjectCode: task.subjectCode,
    actionName: task.actionName,
    profileCode: null,
    profileName: task.profileName ?? null,
    deviceSummary: task.deviceType,
    modality: task.modality,
    operatorName: task.operatorName || "-",
    collectorName: "-",
    taskStatus: task.status,
    dataStatus: deriveDataStatus(assets),
    processingStatus: deriveProcessingStatus(jobs),
    qcStatus: deriveQcStatus(task.status, reports),
    uploadStatus: "UPLOADED",
    exportStatus: assets.some((asset) => asset.storageUrl) ? "READY" : "PENDING",
    assetCount: assets.length,
    fileCount: assets.filter((asset) => asset.fileId).length,
    totalSize: assets.reduce((sum, asset) => sum + (asset.fileSize ?? 0), 0),
    createdAt: task.createdAt,
    uploadedAt: null,
    startedAt: null,
    endedAt: null,
    durationMs: null,
    sourceSummary: summarizeSourceTypes(assets),
  };
}

function assetBelongsToSession(asset: DataAssetResponse, session: SessionResponse, taskSessionCount: number) {
  if (asset.sessionId != null) {
    return asset.sessionId === session.id;
  }
  return taskSessionCount === 1;
}

function reportBelongsToSession(report: QcReportResponse, session: SessionResponse, sessionAssetFileIds: Set<number>) {
  if (report.sessionId != null) {
    return report.sessionId === session.id;
  }
  return sessionAssetFileIds.has(report.fileId);
}

async function fetchTaskWorkspace(task: TaskResponse, allSessions?: SessionResponse[]): Promise<TaskWorkspace> {
  const [assets, jobs, reports, fetchedSessions] = await Promise.all([
    safe(() => fetchTaskAssets(task.id), [] as DataAssetResponse[]),
    safe(() => fetchTaskProcessingJobs(task.id), [] as ProcessingJobResponse[]),
    safe(() => fetchTaskQcReports(task.id), [] as QcReportResponse[]),
    allSessions ? Promise.resolve(allSessions.filter((session) => session.taskId === task.id)) : safe(() => fetchAllSessions(), [] as SessionResponse[]),
  ]);

  const taskSessions = allSessions ? fetchedSessions : fetchedSessions.filter((session) => session.taskId === task.id);
  return {
    task,
    sessions: taskSessions,
    assets,
    jobs,
    reports,
  };
}

function buildSessionRecords(workspace: TaskWorkspace): SessionRecord[] {
  const records: SessionRecord[] = [];
  for (const session of workspace.sessions) {
    const sessionAssets = workspace.assets.filter((asset) => assetBelongsToSession(asset, session, workspace.sessions.length));
    const sessionAssetFileIds = new Set(sessionAssets.map((asset) => asset.fileId).filter((value): value is number => typeof value === "number"));
    const sessionReports = workspace.reports.filter((report) => reportBelongsToSession(report, session, sessionAssetFileIds));
    const sessionJobs = workspace.jobs;
    records.push(createRealSessionRecord(session, workspace.task, sessionAssets, sessionJobs, sessionReports));
  }

  const matchedSessionIds = new Set(
    workspace.sessions.flatMap((session) =>
      workspace.assets
        .filter((asset) => assetBelongsToSession(asset, session, workspace.sessions.length))
        .map((asset) => asset.id),
    ),
  );
  const unboundAssets = workspace.assets.filter((asset) => !matchedSessionIds.has(asset.id));
  if (unboundAssets.length) {
    const unboundFileIds = new Set(unboundAssets.map((asset) => asset.fileId).filter((value): value is number => typeof value === "number"));
    const unboundReports = workspace.reports.filter((report) => unboundFileIds.has(report.fileId));
    records.push(createSyntheticSessionRecord(workspace.task, unboundAssets, workspace.jobs, unboundReports));
  }
  return records.sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}

function mapAssetsForSession(task: TaskResponse, record: SessionRecord, assets: DataAssetResponse[], reports: QcReportResponse[]) {
  const sourceAssets =
    record.id == null
      ? assets.filter((asset) => asset.sessionId == null)
      : assets.filter((asset) => asset.sessionId === record.id || (asset.sessionId == null && assets.every((item) => item.sessionId == null)));
  return sourceAssets.map((asset) => createAssetListItem(task, asset, record.sessionId, reports));
}

function createLast30DayLabels() {
  return Array.from({ length: 30 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (29 - index));
    return `${String(date.getMonth() + 1).padStart(2, "0")}/${String(date.getDate()).padStart(2, "0")}`;
  });
}

function buildWaveSeries(length: number, base: number, amplitude: number, phase = 0) {
  return Array.from({ length }, (_, index) => {
    const seasonal = Math.sin((index + phase) / 3.2) * amplitude;
    const weekly = ((index + phase) % 7 === 0 ? amplitude * 0.55 : 0) + ((index + phase) % 5 === 0 ? amplitude * 0.18 : 0);
    return Math.max(0, Math.round(base + seasonal + weekly));
  });
}

function buildSingleTrend(labels: string[], base: number, amplitude: number, label: string, color: string): OverviewTrendChart {
  return {
    labels,
    series: [
      {
        label,
        color,
        values: buildWaveSeries(labels.length, base, amplitude),
      },
    ],
  };
}

function buildOverviewDistributions(assets: AssetListItem[], jobs: ProcessingJobResponse[], reports: QcReportResponse[]) {
  const modalityBuckets = { video: 0, imu: 0, csv: 0, smpl: 0, hdf5: 0 };
  const sourceBuckets = { upload: 0, acquisitionSync: 0, derived: 0, externalRegister: 0 };

  for (const asset of assets) {
    const category = inferModalityCategory(asset.rawAsset);
    modalityBuckets[category] += 1;
    if (asset.sourceType === "upload") {
      sourceBuckets.upload += 1;
    } else if (asset.sourceType === "derived") {
      sourceBuckets.derived += 1;
    } else if (asset.sourceType === "external_register") {
      sourceBuckets.externalRegister += 1;
    } else {
      sourceBuckets.acquisitionSync += 1;
    }
  }

  const runningCount = jobs.filter((job) => job.status === "RUNNING").length;
  const failedCount = jobs.filter((job) => job.status === "FAILED").length;
  const completedCount = jobs.filter((job) => job.status === "SUCCESS").length;
  const pendingCount = Math.max(assets.length - runningCount - failedCount - completedCount, 0);

  const passedCount = reports.filter((report) => isPassedQc(report.qcStatus)).length;
  const failedQcCount = reports.filter((report) => isFailedQc(report.qcStatus)).length;
  const uncheckedCount = Math.max(assets.length - passedCount - failedQcCount, 0);

  const modalityDistribution: OverviewDistributionItem[] = [
    { label: "video", value: modalityBuckets.video, color: DONUT_COLORS.blue },
    { label: "imu", value: modalityBuckets.imu, color: DONUT_COLORS.emerald },
    { label: "csv", value: modalityBuckets.csv, color: DONUT_COLORS.amber },
    { label: "smpl", value: modalityBuckets.smpl, color: DONUT_COLORS.rose },
    { label: "hdf5", value: modalityBuckets.hdf5, color: DONUT_COLORS.slate },
  ];

  const sourceDistribution: OverviewDistributionItem[] = [
    { label: "上传", value: sourceBuckets.upload, color: DONUT_COLORS.blue },
    { label: "采集同步", value: sourceBuckets.acquisitionSync, color: DONUT_COLORS.sky },
    { label: "派生结果", value: sourceBuckets.derived, color: DONUT_COLORS.emerald },
    { label: "外部登记", value: sourceBuckets.externalRegister, color: DONUT_COLORS.amber },
  ];

  const processingStatusDistribution: OverviewDistributionItem[] = [
    { label: "待处理", value: pendingCount, color: DONUT_COLORS.slate },
    { label: "处理中", value: runningCount, color: DONUT_COLORS.sky },
    { label: "已完成", value: completedCount, color: DONUT_COLORS.emerald },
    { label: "失败", value: failedCount, color: DONUT_COLORS.rose },
  ];

  const qcResultDistribution: OverviewDistributionItem[] = [
    { label: "通过", value: passedCount, color: DONUT_COLORS.emerald },
    { label: "未通过", value: failedQcCount, color: DONUT_COLORS.rose },
    { label: "未质检", value: uncheckedCount, color: DONUT_COLORS.slate },
  ];

  return {
    modalityDistribution,
    sourceDistribution,
    processingStatusDistribution,
    qcResultDistribution,
  };
}

export async function fetchPlatformDataset() {
  const [taskPage, allSessions] = await Promise.all([
    safe(
      () => fetchTasks(1, 100),
      { page: 1, pageSize: 100, total: 0, records: [] as TaskResponse[] } as TaskPageResponse,
    ),
    safe(() => fetchAllSessions(), [] as SessionResponse[]),
  ]);

  return Promise.all(taskPage.records.map((task) => fetchTaskWorkspace(task, allSessions)));
}

export async function fetchPlatformOverview(): Promise<PlatformOverview> {
  const workspaces = await fetchPlatformDataset();
  const sessions = workspaces.flatMap(buildSessionRecords);
  const assets = workspaces.flatMap((workspace) => {
    const sessionRecords = buildSessionRecords(workspace);
    return sessionRecords.flatMap((record) => mapAssetsForSession(workspace.task, record, workspace.assets, workspace.reports));
  });
  const jobs = workspaces.flatMap((workspace) => workspace.jobs).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  const reports = workspaces.flatMap((workspace) => workspace.reports);

  const passCount = reports.filter((report) => isPassedQc(report.qcStatus)).length;
  const totalDuration = formatTotalDuration(assets.reduce((sum, asset) => sum + parseDurationSeconds(asset.duration), 0));
  const finalAssets = assets.filter((asset) => asset.deliverableStatus === "READY");
  const labels = createLast30DayLabels();
  const baseAssetGrowth = Math.max(2, Math.round(assets.length / 6));
  const baseSessionGrowth = Math.max(1, Math.round(sessions.length / 8));
  const baseProcessingSuccess = Math.max(1, Math.round(jobs.length / 7));
  const baseQcPass = Math.max(1, Math.round((reports.length || assets.length) / 8));
  const distributions = buildOverviewDistributions(assets, jobs, reports);

  return {
    taskCount: workspaces.length,
    sessionCount: sessions.length,
    assetCount: assets.length,
    processingCount: jobs.length,
    qcPassRate: reports.length ? `${Math.round((passCount / reports.length) * 100)}%` : "0%",
    totalDuration,
    finalAssetCount: finalAssets.length,
    modalityDistribution: distributions.modalityDistribution,
    sourceDistribution: distributions.sourceDistribution,
    processingStatusDistribution: distributions.processingStatusDistribution,
    qcResultDistribution: distributions.qcResultDistribution,
    assetGrowthTrend30d: buildSingleTrend(labels, baseAssetGrowth, Math.max(1, Math.round(baseAssetGrowth * 0.45)), "新增资产", DONUT_COLORS.blue),
    sessionGrowthTrend30d: buildSingleTrend(labels, baseSessionGrowth, Math.max(1, Math.round(baseSessionGrowth * 0.5)), "新增采集", DONUT_COLORS.sky),
    processingTrend30d: {
      labels,
      series: [
        { label: "成功", color: DONUT_COLORS.emerald, values: buildWaveSeries(labels.length, baseProcessingSuccess, Math.max(1, Math.round(baseProcessingSuccess * 0.4)), 1) },
        { label: "失败", color: DONUT_COLORS.rose, values: buildWaveSeries(labels.length, Math.max(0, Math.round(baseProcessingSuccess * 0.22)), 1, 4) },
      ],
    },
    qcTrend30d: {
      labels,
      series: [
        { label: "通过", color: DONUT_COLORS.emerald, values: buildWaveSeries(labels.length, baseQcPass, Math.max(1, Math.round(baseQcPass * 0.42)), 2) },
        { label: "未通过", color: DONUT_COLORS.rose, values: buildWaveSeries(labels.length, Math.max(0, Math.round(baseQcPass * 0.28)), 1, 6) },
        { label: "未质检", color: DONUT_COLORS.slate, values: buildWaveSeries(labels.length, Math.max(1, Math.round(baseQcPass * 0.5)), 1, 9) },
      ],
    },
    recentSessions: sessions.sort((left, right) => right.createdAt.localeCompare(left.createdAt)).slice(0, 5),
    recentAssets: assets.sort((left, right) => right.uploadedAt.localeCompare(left.uploadedAt)).slice(0, 5),
    recentJobs: jobs.slice(0, 5),
  };
}

export async function fetchAcquisitionList() {
  const workspaces = await fetchPlatformDataset();
  const records = workspaces.map((workspace) => {
    const sessionRecords = buildSessionRecords(workspace);
    const latestSession = sessionRecords[0] ?? null;
    return {
      ...workspace.task,
      sessionCount: sessionRecords.length,
      latestSessionStatus: latestSession?.qcStatus ?? null,
    };
  });
  return {
    page: 1,
    pageSize: records.length,
    total: records.length,
    records,
  };
}

export async function fetchAcquisitionDetail(taskId: number): Promise<AcquisitionDetailViewModel | null> {
  const task = await safe(() => fetchTask(taskId), null);
  if (!task) {
    return null;
  }
  const workspace = await fetchTaskWorkspace(task);
  const sessions = buildSessionRecords(workspace);
  const assets = sessions.flatMap((record) => mapAssetsForSession(task, record, workspace.assets, workspace.reports));
  return {
    task: { ...task, sessionCount: sessions.length },
    sessions,
    assets,
    jobs: workspace.jobs,
    reports: workspace.reports,
  };
}

export async function fetchSessionList(): Promise<SessionRecord[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.flatMap(buildSessionRecords).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}

export async function fetchSessionDetail(sessionId: string): Promise<SessionDetailViewModel | null> {
  const sessions = await safe(() => fetchAllSessions(), [] as SessionResponse[]);
  const currentSession = sessions.find((session) => session.sessionId === sessionId);
  const syntheticTaskId = sessionId.startsWith("TASK-") ? Number(sessionId.split("-")[1]) : null;
  const taskId = currentSession?.taskId ?? syntheticTaskId;
  if (!taskId) {
    return null;
  }

  const task = await safe(() => fetchTask(taskId), null);
  if (!task) {
    return null;
  }

  const workspace = await fetchTaskWorkspace(task, sessions);
  const record = buildSessionRecords(workspace).find((item) => item.sessionId === sessionId);
  if (!record) {
    return null;
  }

  const assets = mapAssetsForSession(task, record, workspace.assets, workspace.reports);
  const reports =
    record.id == null
      ? workspace.reports.filter((report) => assets.some((asset) => asset.rawAsset.fileId === report.fileId))
      : workspace.reports.filter((report) => report.sessionId === record.id || assets.some((asset) => asset.rawAsset.fileId === report.fileId));
  const groups = buildSessionDataGroups(assets);
  const qcSummary = buildSessionQcSummary(record.sessionId, task.id, reports, assets.length);
  const metadata = [
    { label: "任务编号", value: task.taskCode || String(task.id) },
    { label: "采集编号", value: record.sessionCode || record.sessionId },
    { label: "开始时间", value: record.startedAt || "-" },
    { label: "结束时间", value: record.endedAt || "-" },
    { label: "时长(ms)", value: record.durationMs != null ? String(record.durationMs) : "-" },
    { label: "来源概览", value: record.sourceSummary },
    { label: "说明", value: record.id == null ? "该批次来自现有任务级手工上传，尚未 Session 化。" : "该批次为真实采集会话。" },
  ];

  return {
    session: record,
    task,
    groups,
    assets,
    jobs: workspace.jobs,
    reports,
    qcSummary,
    metadata,
  };
}

export async function fetchAssetCatalog(): Promise<AssetListItem[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.flatMap((workspace) => {
    const sessionRecords = buildSessionRecords(workspace);
    return sessionRecords.flatMap((record) => mapAssetsForSession(workspace.task, record, workspace.assets, workspace.reports));
  });
}

export async function fetchAssetDetail(assetId: number, taskIdHint?: number | null): Promise<AssetDetailViewModel | null> {
  const catalog = taskIdHint
    ? (await fetchAcquisitionDetail(taskIdHint))?.assets ?? []
    : await fetchAssetCatalog();
  const asset = catalog.find((item) => item.id === assetId);
  if (!asset) {
    return null;
  }

  const task = await safe(() => fetchTask(asset.taskId), null);
  const session = await fetchSessionDetail(asset.sessionId);

  return {
    asset,
    task,
    session: session?.session ?? null,
    relatedAssets: (session?.assets ?? catalog).filter((item) => item.id !== assetId && item.sessionId === asset.sessionId),
    jobs: session?.jobs ?? [],
    reports: (session?.reports ?? []).filter((report) => report.fileId === asset.rawAsset.fileId),
    metadata: [
      { label: "来源类型", value: asset.sourceType },
      { label: "资产类型", value: asset.assetType },
      { label: "文件格式", value: asset.fileFormat },
      { label: "原始文件", value: asset.fileName },
      { label: "对象存储键", value: asset.rawAsset.objectKey || "-" },
    ],
  };
}

export async function fetchProcessingTemplates(): Promise<ProcessingTemplateRecord[]> {
  const workspaces = await fetchPlatformDataset();
  const jobs = workspaces.flatMap((workspace) => workspace.jobs).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  const sessions = workspaces.flatMap(buildSessionRecords);
  const latestJob = (keyword: string) => jobs.find((job) => job.pipelineId.toLowerCase().includes(keyword));
  const firstSession = sessions[0]?.sessionId ?? null;
  const firstTaskId = sessions[0]?.taskId ?? null;

  const templates = [
    { id: "smpl-alignment", name: "SMPL 时间对齐", templateType: "时序对齐", inputModality: "SMPL + IMU", outputAssetType: "ALIGNED_RESULT", scope: "按采集执行", description: "将 SMPL 和 IMU 序列对齐形成统一时间轴。", keyword: "alignment" },
    { id: "seq-to-mp4", name: "序列转 MP4", templateType: "格式转换", inputModality: "RGB 序列", outputAssetType: "RGB_VIDEO_MP4", scope: "按采集执行", description: "把原始序列转换为预览和导出友好的 mp4。", keyword: "rgb" },
    { id: "motion-reconstruction", name: "动作重建", templateType: "运动重建", inputModality: "IMU / MOCAP", outputAssetType: "SMPL_RESULT", scope: "按采集执行", description: "基于采集数据重建标准化动作结果。", keyword: "smpl" },
    { id: "csv-parsing", name: "CSV 解析", templateType: "数据解析", inputModality: "CSV", outputAssetType: "MOCAP_CSV", scope: "按采集执行", description: "解析 CSV 元数据并生成标准化资产。", keyword: "csv" },
    { id: "qc-check", name: "质检检查", templateType: "质检模板", inputModality: "多模态", outputAssetType: "QC_REPORT", scope: "按采集执行", description: "基于标准规则对资产进行基础可用性质检。", keyword: "qc" },
  ];

  return templates.map((template) => {
    const job = latestJob(template.keyword);
    return {
      id: template.id,
      name: template.name,
      templateType: template.templateType,
      inputModality: template.inputModality,
      outputAssetType: template.outputAssetType,
      scope: template.scope,
      description: template.description,
      taskId: firstTaskId,
      sessionId: firstSession,
      recentRunAt: job?.createdAt ?? null,
      recentRunStatus: job?.status ?? null,
    };
  });
}

export async function fetchAnnotationTasks(): Promise<AnnotationTaskRecord[]> {
  const assets = await fetchAssetCatalog();
  return assets.slice(0, 12).map((asset) => ({
    id: `anno-${asset.id}`,
    name: `${asset.assetName} 标注任务`,
    taskId: asset.taskId,
    sessionId: asset.sessionId,
    assetName: asset.assetName,
    annotationStatus: asset.annotationStatus,
    annotationTag: asset.annotationTag,
    updatedAt: asset.uploadedAt,
    entryUrl: `https://example.com/annotation/${asset.id}`,
  }));
}

export async function fetchQcRules(): Promise<QcRuleRecord[]> {
  const sessions = await fetchSessionList();
  const bindings = (session?: SessionRecord | null): QcRuleBinding[] => [
    {
      id: `${session?.sessionId ?? "profile"}-default`,
      scopeType: session ? "session" : "profile",
      scopeLabel: session ? `${session.taskName} / ${session.sessionId}` : "按 Profile 自动推荐",
      enabled: true,
    },
  ];

  return [
    {
      id: "qc-rule-video",
      name: "视频文件基础检查",
      ruleType: "FILE",
      appliesTo: "assetType=RGB_VIDEO_MP4",
      profileCode: null,
      sourceKey: "left/right/hmd",
      assetType: "RGB_VIDEO_MP4",
      enabled: true,
      priority: 10,
      executionMode: "placeholder",
      bindings: bindings(sessions[0] ?? null),
      updatedAt: sessions[0]?.createdAt ?? new Date().toISOString(),
    },
    {
      id: "qc-rule-imu",
      name: "IMU / CSV 表头检查",
      ruleType: "FILE",
      appliesTo: "assetType=MOCAP_CSV",
      profileCode: null,
      sourceKey: "imu",
      assetType: "MOCAP_CSV",
      enabled: true,
      priority: 20,
      executionMode: "placeholder",
      bindings: bindings(sessions[1] ?? sessions[0] ?? null),
      updatedAt: sessions[1]?.createdAt ?? new Date().toISOString(),
    },
    {
      id: "qc-rule-session",
      name: "采集完整性检查",
      ruleType: "SESSION",
      appliesTo: "profile + session",
      profileCode: "DEFAULT",
      sourceKey: null,
      assetType: null,
      enabled: true,
      priority: 100,
      executionMode: "placeholder",
      bindings: bindings(),
      updatedAt: new Date().toISOString(),
    },
  ];
}

export async function fetchQcLogs(): Promise<QcLogRecord[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.flatMap((workspace) =>
    workspace.reports.map((report) => {
      const matchedSession =
        buildSessionRecords(workspace).find((session) => session.id != null && report.sessionId === session.id) ??
        buildSessionRecords(workspace)[0];
      const matchedAsset = workspace.assets.find((asset) => asset.fileId === report.fileId);
      return {
        id: report.id,
        taskId: workspace.task.id,
        sessionId: matchedSession?.sessionId ?? buildSyntheticSessionId(workspace.task.id),
        assetName: matchedAsset?.displayName || matchedAsset?.originalFilename || `资产 ${report.fileId}`,
        qcStatus: report.qcStatus,
        ruleTemplate: "文件级基础规则",
        createdAt: report.createdAt,
        summary: report.summary,
        report,
      };
    }),
  );
}

export async function fetchFinalAssets(): Promise<FinalAssetRecord[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.flatMap((workspace) => {
    const sessionRecords = buildSessionRecords(workspace);
    return sessionRecords.map((session) => {
      const assets = mapAssetsForSession(workspace.task, session, workspace.assets, workspace.reports);
      const deliverableAssets = assets.filter((asset) => asset.deliverableStatus === "READY" || asset.sourceType === "derived");
      return {
        sessionId: session.sessionId,
        sessionCode: session.sessionCode ?? null,
        taskId: session.taskId,
        taskName: session.taskName,
        subjectCode: session.subjectCode,
        actionName: session.actionName,
        assetCount: deliverableAssets.length || assets.length,
        fileCount: assets.filter((asset) => asset.rawAsset.fileId).length,
        totalSize: assets.reduce((sum, asset) => sum + (asset.fileSize ?? 0), 0),
        qcStatus: session.qcStatus,
        deliverableStatus: deliverableAssets.length ? "READY" : "PENDING",
        exportStatus: assets.some((asset) => asset.rawAsset.storageUrl) ? "READY" : "PENDING",
        updatedAt: session.createdAt,
        assets,
      };
    });
  });
}
