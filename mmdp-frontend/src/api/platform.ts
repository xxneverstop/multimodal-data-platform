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

function mapSourceType(asset: DataAssetResponse): "upload" | "acquisition_sync" | "derived" {
  if (asset.producedByJobId) {
    return "derived";
  }
  if (asset.sourceType === "UPLOADED_FILE") {
    return "upload";
  }
  return "acquisition_sync";
}

function inferModality(asset: DataAssetResponse, task: TaskResponse) {
  const type = asset.assetType.toUpperCase();
  if (type.includes("SMPL")) {
    return "SMPL";
  }
  if (type.includes("IMU") || type.includes("MOCAP")) {
    return "IMU";
  }
  if (type.includes("RGB") || type.includes("VIDEO")) {
    return "视频";
  }
  return task.modality || "多模态";
}

function inferDuration(asset: DataAssetResponse) {
  const type = asset.assetType.toUpperCase();
  if (type.includes("VIDEO") || type.includes("SEQ")) {
    return "00:02:16";
  }
  if (type.includes("SMPL") || type.includes("MOCAP")) {
    return "00:01:48";
  }
  return "-";
}

function inferAnnotationStatus(assetId: number) {
  return ANNOTATION_STATUSES[assetId % ANNOTATION_STATUSES.length];
}

function inferAnnotationTag(assetId: number) {
  return ANNOTATION_TAGS[assetId % ANNOTATION_TAGS.length];
}

function inferDeliverableStatus(item: Pick<AssetListItem, "qcStatus" | "annotationStatus" | "sourceType">) {
  if (item.qcStatus === "PASSED" || item.qcStatus === "QC_PASSED") {
    if (item.annotationStatus === "已标注" || item.sourceType === "derived") {
      return "READY";
    }
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
    modality: inferModality(asset, task),
    fileFormat: asset.fileFormat || asset.fileExt || "-",
    sourceType: mapSourceType(asset),
    annotationStatus: inferAnnotationStatus(asset.id),
    annotationTag: inferAnnotationTag(asset.id),
    qcStatus: qcReport?.qcStatus || task.status || "PENDING",
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

export async function fetchPlatformDataset() {
  const taskPage = await safe(() => fetchTasks(1, 100), { current: 1, size: 100, total: 0, records: [] as TaskResponse[] });
  return Promise.all(taskPage.records.map(fetchTaskWorkspace));
}

export async function fetchPlatformOverview(): Promise<PlatformOverview> {
  const workspaces = await fetchPlatformDataset();
  const sessions = workspaces.map(({ task, assets, jobs, reports }) => createSession(task, assets, jobs, reports));
  const assets = workspaces.flatMap(({ task, assets, reports }) => assets.map((asset) => mapAsset(task, asset, buildSessionId(task.id), reports)));
  const jobs = workspaces.flatMap(({ jobs }) => jobs).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  const reports = workspaces.flatMap(({ reports }) => reports);
  const passCount = reports.filter((report) => report.qcStatus === "PASSED" || report.qcStatus === "QC_PASSED").length;
  return {
    taskCount: workspaces.length,
    sessionCount: sessions.length,
    assetCount: assets.length,
    processingCount: jobs.length,
    qcPassRate: reports.length ? `${Math.round((passCount / reports.length) * 100)}%` : "0%",
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
  return workspaces.flatMap(({ task, assets, reports }) => assets.map((asset) => mapAsset(task, asset, buildSessionId(task.id), reports)));
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
  const jobs = workspaces.flatMap(({ jobs }) => jobs).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  const latestJob = (keyword: string) => jobs.find((job) => job.pipelineId.toLowerCase().includes(keyword));
  const firstSession = workspaces[0]?.task ? buildSessionId(workspaces[0].task.id) : null;
  const firstTaskId = workspaces[0]?.task.id ?? null;
  const templates = [
    { id: "smpl-alignment", name: "SMPL 时间对齐", templateType: "时序对齐", inputModality: "SMPL + IMU", outputAssetType: "ALIGNED_RESULT", scope: "按 session 执行", description: "将 SMPL 与 IMU 序列对齐，形成统一时间基线。", keyword: "alignment" },
    { id: "seq-to-mp4", name: "seq 转 mp4", templateType: "格式转换", inputModality: "RGB 序列", outputAssetType: "RGB_VIDEO_MP4", scope: "按 session 执行", description: "把原始序列转换为便于预览和导出的 mp4 成品。", keyword: "rgb" },
    { id: "motion-reconstruction", name: "motion reconstruction", templateType: "运动重建", inputModality: "IMU / MOCAP", outputAssetType: "SMPL_RESULT", scope: "按 session 执行", description: "基于采集数据重建标准化动作结果。", keyword: "smpl" },
    { id: "csv-parsing", name: "CSV 解析", templateType: "数据解析", inputModality: "CSV", outputAssetType: "MOCAP_CSV", scope: "按 session 执行", description: "解析 CSV 元数据并生成标准化资产。", keyword: "csv" },
    { id: "qc-check", name: "质检检查", templateType: "质检模板", inputModality: "多模态", outputAssetType: "QC_REPORT", scope: "按 session 执行", description: "基于标准规则对资产做基础可用性质检。", keyword: "qc" }
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
    { id: "qc-1", name: "采集完整性检查", templateName: "基础采集规则", scope: "session", taskId: sessions[0]?.taskId ?? null, sessionId: sessions[0]?.sessionId ?? null, status: "READY", updatedAt: sessions[0]?.createdAt ?? new Date().toISOString() },
    { id: "qc-2", name: "时序漂移检查", templateName: "同步质检模板", scope: "session", taskId: sessions[1]?.taskId ?? null, sessionId: sessions[1]?.sessionId ?? null, status: "READY", updatedAt: sessions[1]?.createdAt ?? new Date().toISOString() }
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
    .filter((asset) => asset.qcStatus === "PASSED" || asset.qcStatus === "QC_PASSED" || asset.sourceType === "derived")
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
