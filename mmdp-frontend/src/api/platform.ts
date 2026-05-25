import { fetchTaskAssets } from "@/api/assets";
import { fetchTaskQcReports } from "@/api/qc";
import { fetchTaskProcessingJobs } from "@/api/processing";
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
  QcRuleRecord,
  SessionDetailViewModel,
  SessionRecord
} from "@/types/platform";
import type { QcReportResponse } from "@/types/qc";
import type { TaskResponse } from "@/types/task";

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
  slate: "#94a3b8"
};

async function safe<T>(loader: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await loader();
  } catch {
    return fallback;
  }
}

function buildSessionId(taskId: number) {
  return `SESS-${String(taskId).padStart(4, "0")}`;
}

function deriveProcessingStatus(jobs: ProcessingJobResponse[]) {
  const latestJob = [...jobs].sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0];
  return latestJob?.status ?? "PENDING";
}

function deriveQcStatus(taskStatus: string, reports: QcReportResponse[]) {
  const latest = [...reports].sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0];
  return latest?.qcStatus ?? taskStatus ?? "PENDING";
}

function deriveDataStatus(assets: DataAssetResponse[]) {
  if (!assets.length) {
    return "PENDING";
  }
  if (assets.some((asset) => asset.producedByJobId)) {
    return "READY";
  }
  return "UPLOADING";
}

function mapSourceType(asset: DataAssetResponse): AssetListItem["sourceType"] {
  if (asset.producedByJobId) {
    return "derived";
  }
  if (asset.sourceType === "EXTERNAL_PATH") {
    return "external_register";
  }
  if (asset.sourceType === "UPLOADED_FILE") {
    return "upload";
  }
  return "acquisition_sync";
}

function inferModalityCategory(asset: DataAssetResponse) {
  const assetType = asset.assetType.toUpperCase();
  const fileFormat = String(asset.fileFormat || asset.fileExt || "").toLowerCase();
  if (assetType.includes("SMPL")) {
    return "smpl";
  }
  if (assetType.includes("MOCAP") || assetType.includes("IMU")) {
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

function inferDuration(asset: DataAssetResponse) {
  const category = inferModalityCategory(asset);
  if (category === "video") {
    return "00:05:20";
  }
  if (category === "imu") {
    return "00:03:40";
  }
  if (category === "smpl") {
    return "00:02:45";
  }
  if (category === "hdf5") {
    return "00:04:10";
  }
  return "00:01:15";
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

function inferAnnotationStatus(assetId: number) {
  return ANNOTATION_STATUSES[assetId % ANNOTATION_STATUSES.length];
}

function inferAnnotationTag(assetId: number) {
  return ANNOTATION_TAGS[assetId % ANNOTATION_TAGS.length];
}

function inferDeliverableStatus(item: Pick<AssetListItem, "qcStatus" | "annotationStatus" | "sourceType">) {
  const passedQc = item.qcStatus === "PASSED" || item.qcStatus === "QC_PASSED";
  const annotated = item.annotationStatus === "已标注";
  if (passedQc && (annotated || item.sourceType === "derived")) {
    return "READY";
  }
  return "PENDING";
}

function createSession(task: TaskResponse, assets: DataAssetResponse[], jobs: ProcessingJobResponse[], reports: QcReportResponse[]): SessionRecord {
  return {
    sessionId: buildSessionId(task.id),
    taskId: task.id,
    taskName: task.taskName,
    sessionName: `${task.taskName} 会话`,
    subjectCode: task.subjectCode,
    actionName: task.actionName,
    deviceSummary: task.deviceType,
    modality: task.modality,
    operatorName: task.operatorName || "-",
    taskStatus: task.status,
    dataStatus: deriveDataStatus(assets),
    processingStatus: deriveProcessingStatus(jobs),
    qcStatus: deriveQcStatus(task.status, reports),
    assetCount: assets.length,
    createdAt: task.createdAt
  };
}

function mapAsset(task: TaskResponse, asset: DataAssetResponse, sessionId: string, reports: QcReportResponse[]): AssetListItem {
  const qcReport = reports.find((report) => report.fileId === asset.fileId);
  const item: AssetListItem = {
    id: asset.id,
    taskId: task.id,
    sessionId,
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
    qcStatus: qcReport?.qcStatus || "PENDING",
    processingStatus: asset.producedByJobId ? "SUCCESS" : "PENDING",
    deliverableStatus: "PENDING",
    rawAsset: asset
  };
  item.deliverableStatus = inferDeliverableStatus(item);
  return item;
}

async function fetchTaskWorkspace(task: TaskResponse) {
  const [assets, jobs, reports] = await Promise.all([
    safe(() => fetchTaskAssets(task.id), [] as DataAssetResponse[]),
    safe(() => fetchTaskProcessingJobs(task.id), [] as ProcessingJobResponse[]),
    safe(() => fetchTaskQcReports(task.id), [] as QcReportResponse[])
  ]);
  return { task, assets, jobs, reports };
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
        values: buildWaveSeries(labels.length, base, amplitude)
      }
    ]
  };
}

function buildOverviewDistributions(
  assets: AssetListItem[],
  jobs: ProcessingJobResponse[],
  reports: QcReportResponse[]
) {
  const modalityBuckets = {
    video: 0,
    imu: 0,
    csv: 0,
    smpl: 0,
    hdf5: 0
  };

  const sourceBuckets = {
    upload: 0,
    acquisitionSync: 0,
    derived: 0,
    externalRegister: 0
  };

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

  const passedCount = reports.filter((report) => report.qcStatus === "PASSED" || report.qcStatus === "QC_PASSED").length;
  const failedQcCount = reports.filter((report) => report.qcStatus === "FAILED" || report.qcStatus === "QC_FAILED").length;
  const uncheckedCount = Math.max(assets.length - passedCount - failedQcCount, 0);

  const modalityDistribution: OverviewDistributionItem[] = [
    { label: "video", value: modalityBuckets.video, color: DONUT_COLORS.blue },
    { label: "imu", value: modalityBuckets.imu, color: DONUT_COLORS.emerald },
    { label: "csv", value: modalityBuckets.csv, color: DONUT_COLORS.amber },
    { label: "smpl", value: modalityBuckets.smpl, color: DONUT_COLORS.rose },
    { label: "hdf5", value: modalityBuckets.hdf5, color: DONUT_COLORS.slate }
  ];

  const sourceDistribution: OverviewDistributionItem[] = [
    { label: "上传", value: sourceBuckets.upload, color: DONUT_COLORS.blue },
    { label: "采集同步", value: sourceBuckets.acquisitionSync, color: DONUT_COLORS.sky },
    { label: "派生结果", value: sourceBuckets.derived, color: DONUT_COLORS.emerald },
    { label: "外部登记", value: sourceBuckets.externalRegister, color: DONUT_COLORS.amber }
  ];

  const processingStatusDistribution: OverviewDistributionItem[] = [
    { label: "待处理", value: pendingCount, color: DONUT_COLORS.slate },
    { label: "处理中", value: runningCount, color: DONUT_COLORS.sky },
    { label: "已完成", value: completedCount, color: DONUT_COLORS.emerald },
    { label: "失败", value: failedCount, color: DONUT_COLORS.rose }
  ];

  const qcResultDistribution: OverviewDistributionItem[] = [
    { label: "通过", value: passedCount, color: DONUT_COLORS.emerald },
    { label: "不通过", value: failedQcCount, color: DONUT_COLORS.rose },
    { label: "未质检", value: uncheckedCount, color: DONUT_COLORS.slate }
  ];

  return {
    modalityDistribution,
    sourceDistribution,
    processingStatusDistribution,
    qcResultDistribution
  };
}

export async function fetchPlatformDataset() {
  const taskPage = await safe(() => fetchTasks(1, 100), {
    current: 1,
    size: 100,
    total: 0,
    records: [] as TaskResponse[]
  });
  return Promise.all(taskPage.records.map(fetchTaskWorkspace));
}

export async function fetchPlatformOverview(): Promise<PlatformOverview> {
  const workspaces = await fetchPlatformDataset();
  const sessions = workspaces.map(({ task, assets, jobs, reports }) => createSession(task, assets, jobs, reports));
  const assets = workspaces.flatMap(({ task, assets, reports }) =>
    assets.map((asset) => mapAsset(task, asset, buildSessionId(task.id), reports))
  );
  const jobs = workspaces
    .flatMap(({ jobs }) => jobs)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  const reports = workspaces.flatMap(({ reports }) => reports);

  const passCount = reports.filter((report) => report.qcStatus === "PASSED" || report.qcStatus === "QC_PASSED").length;
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
    sessionGrowthTrend30d: buildSingleTrend(labels, baseSessionGrowth, Math.max(1, Math.round(baseSessionGrowth * 0.5)), "新增批次", DONUT_COLORS.sky),
    processingTrend30d: {
      labels,
      series: [
        {
          label: "成功",
          color: DONUT_COLORS.emerald,
          values: buildWaveSeries(labels.length, baseProcessingSuccess, Math.max(1, Math.round(baseProcessingSuccess * 0.4)), 1)
        },
        {
          label: "失败",
          color: DONUT_COLORS.rose,
          values: buildWaveSeries(labels.length, Math.max(0, Math.round(baseProcessingSuccess * 0.22)), 1, 4)
        }
      ]
    },
    qcTrend30d: {
      labels,
      series: [
        {
          label: "通过",
          color: DONUT_COLORS.emerald,
          values: buildWaveSeries(labels.length, baseQcPass, Math.max(1, Math.round(baseQcPass * 0.42)), 2)
        },
        {
          label: "不通过",
          color: DONUT_COLORS.rose,
          values: buildWaveSeries(labels.length, Math.max(0, Math.round(baseQcPass * 0.28)), 1, 6)
        },
        {
          label: "未质检",
          color: DONUT_COLORS.slate,
          values: buildWaveSeries(labels.length, Math.max(1, Math.round(baseQcPass * 0.5)), 1, 9)
        }
      ]
    },
    recentSessions: sessions.sort((left, right) => right.createdAt.localeCompare(left.createdAt)).slice(0, 5),
    recentAssets: assets.sort((left, right) => right.uploadedAt.localeCompare(left.uploadedAt)).slice(0, 5),
    recentJobs: jobs.slice(0, 5)
  };
}

export async function fetchAcquisitionList() {
  return fetchTasks(1, 100);
}

export async function fetchAcquisitionDetail(taskId: number): Promise<AcquisitionDetailViewModel | null> {
  const task = await safe(() => fetchTask(taskId), null);
  if (!task) {
    return null;
  }

  const { assets, jobs, reports } = await fetchTaskWorkspace(task);
  const session = createSession(task, assets, jobs, reports);

  return {
    task,
    sessions: [session],
    assets: assets.map((asset) => mapAsset(task, asset, session.sessionId, reports)),
    jobs,
    reports
  };
}

export async function fetchSessionList(): Promise<SessionRecord[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.map(({ task, assets, jobs, reports }) => createSession(task, assets, jobs, reports));
}

export async function fetchSessionDetail(sessionId: string): Promise<SessionDetailViewModel | null> {
  const taskId = Number(sessionId.replace("SESS-", ""));
  if (!taskId) {
    return null;
  }

  const detail = await fetchAcquisitionDetail(taskId);
  if (!detail) {
    return null;
  }

  return {
    session: detail.sessions[0],
    task: detail.task,
    assets: detail.assets,
    jobs: detail.jobs,
    reports: detail.reports
  };
}

export async function fetchAssetCatalog(): Promise<AssetListItem[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.flatMap(({ task, assets, reports }) =>
    assets.map((asset) => mapAsset(task, asset, buildSessionId(task.id), reports))
  );
}

export async function fetchAssetDetail(assetId: number, taskIdHint?: number | null): Promise<AssetDetailViewModel | null> {
  if (taskIdHint) {
    const detail = await fetchAcquisitionDetail(taskIdHint);
    if (!detail) {
      return null;
    }

    const asset = detail.assets.find((item) => item.id === assetId);
    if (!asset) {
      return null;
    }

    return {
      asset,
      task: detail.task,
      session: detail.sessions[0] ?? null,
      relatedAssets: detail.assets.filter((item) => item.id !== assetId),
      jobs: detail.jobs.filter((job) => job.id === asset.rawAsset.producedByJobId || job.taskId === detail.task.id),
      reports: detail.reports.filter((report) => report.fileId === asset.rawAsset.fileId),
      metadata: [
        { label: "来源类型", value: asset.sourceType },
        { label: "文件格式", value: asset.fileFormat },
        { label: "描述", value: asset.rawAsset.description || "-" },
        { label: "备注", value: asset.rawAsset.operatorRemark || "-" }
      ]
    };
  }

  const assets = await fetchAssetCatalog();
  const matched = assets.find((item) => item.id === assetId);
  if (!matched) {
    return null;
  }

  return fetchAssetDetail(assetId, matched.taskId);
}

export async function fetchProcessingTemplates(): Promise<ProcessingTemplateRecord[]> {
  const workspaces = await fetchPlatformDataset();
  const jobs = workspaces
    .flatMap(({ jobs }) => jobs)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  const latestJob = (keyword: string) => jobs.find((job) => job.pipelineId.toLowerCase().includes(keyword));
  const firstSession = workspaces[0]?.task ? buildSessionId(workspaces[0].task.id) : null;
  const firstTaskId = workspaces[0]?.task.id ?? null;

  const templates = [
    {
      id: "smpl-alignment",
      name: "SMPL 时间对齐",
      templateType: "时序对齐",
      inputModality: "SMPL + IMU",
      outputAssetType: "ALIGNED_RESULT",
      scope: "按采集批次执行",
      description: "将 SMPL 与 IMU 序列对齐，形成统一时间基线。",
      keyword: "alignment"
    },
    {
      id: "seq-to-mp4",
      name: "seq 转 mp4",
      templateType: "格式转换",
      inputModality: "RGB 序列",
      outputAssetType: "RGB_VIDEO_MP4",
      scope: "按采集批次执行",
      description: "把原始序列转换为便于预览和导出的 mp4 成品。",
      keyword: "rgb"
    },
    {
      id: "motion-reconstruction",
      name: "motion reconstruction",
      templateType: "运动重建",
      inputModality: "IMU / MOCAP",
      outputAssetType: "SMPL_RESULT",
      scope: "按采集批次执行",
      description: "基于采集数据重建标准化动作结果。",
      keyword: "smpl"
    },
    {
      id: "csv-parsing",
      name: "CSV 解析",
      templateType: "数据解析",
      inputModality: "CSV",
      outputAssetType: "MOCAP_CSV",
      scope: "按采集批次执行",
      description: "解析 CSV 元数据并生成标准化资产。",
      keyword: "csv"
    },
    {
      id: "qc-check",
      name: "质检检查",
      templateType: "质检模板",
      inputModality: "多模态",
      outputAssetType: "QC_REPORT",
      scope: "按采集批次执行",
      description: "基于标准规则对资产进行基础可用性质检。",
      keyword: "qc"
    }
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
      recentRunStatus: job?.status ?? null
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
    entryUrl: `https://example.com/annotation/${asset.id}`
  }));
}

export async function fetchQcRules(): Promise<QcRuleRecord[]> {
  const sessions = await fetchSessionList();
  return [
    {
      id: "qc-1",
      name: "采集完整性检查",
      templateName: "基础采集规则",
      scope: "session",
      taskId: sessions[0]?.taskId ?? null,
      sessionId: sessions[0]?.sessionId ?? null,
      status: "READY",
      updatedAt: sessions[0]?.createdAt ?? new Date().toISOString()
    },
    {
      id: "qc-2",
      name: "时序漂移检查",
      templateName: "同步质检模板",
      scope: "session",
      taskId: sessions[1]?.taskId ?? null,
      sessionId: sessions[1]?.sessionId ?? null,
      status: "READY",
      updatedAt: sessions[1]?.createdAt ?? new Date().toISOString()
    }
  ];
}

export async function fetchQcLogs(): Promise<QcLogRecord[]> {
  const workspaces = await fetchPlatformDataset();
  return workspaces.flatMap(({ task, assets, reports }) =>
    reports.map((report) => {
      const asset = assets.find((item) => item.fileId === report.fileId);
      return {
        id: report.id,
        taskId: task.id,
        sessionId: buildSessionId(task.id),
        assetName: asset?.displayName || asset?.originalFilename || `资产 ${report.fileId}`,
        qcStatus: report.qcStatus,
        ruleTemplate: "基础采集规则",
        createdAt: report.createdAt,
        summary: report.summary,
        report
      };
    })
  );
}

export async function fetchFinalAssets(): Promise<FinalAssetRecord[]> {
  const assets = await fetchAssetCatalog();
  return assets
    .filter((asset) => asset.deliverableStatus === "READY" || asset.sourceType === "derived")
    .map((asset) => ({
      id: asset.id,
      taskId: asset.taskId,
      sessionId: asset.sessionId,
      assetName: asset.assetName,
      assetType: asset.assetType,
      modality: asset.modality,
      fileFormat: asset.fileFormat,
      processingStatus: asset.processingStatus,
      annotationStatus: asset.annotationStatus,
      qcStatus: asset.qcStatus,
      deliverableStatus: asset.deliverableStatus,
      updatedAt: asset.uploadedAt
    }));
}
