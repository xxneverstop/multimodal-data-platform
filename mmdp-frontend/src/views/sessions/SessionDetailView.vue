<template>
  <div v-if="detail" class="light2-page">
    <div class="light2-detail-breadcrumb">
      <RouterLink to="/sessions">采集会话</RouterLink>
      <span>/</span>
      <span>{{ detail.session.sessionCode || detail.session.sessionId }}</span>
    </div>

    <div class="light2-hdr">
      <div class="min-w-0">
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <StatusBadge :status="detail.session.uploadStatus" />
          <StatusBadge :status="detail.session.qcStatus" />
          <StatusBadge :status="detail.session.exportStatus" />
          <StatusBadge :status="sessionPrimaryStatus" :label="sessionPrimaryLabel" />
        </div>
        <h1>
          采集会话
          <span class="light2-hdr-code">{{ detail.session.sessionCode || detail.session.sessionId }}</span>
        </h1>
        <p>
          {{ detail.session.subjectCode || "-" }}
          ·
          {{ detail.session.actionName || "-" }}
          ·
          {{ detail.task?.taskName || detail.session.taskName || "-" }}
          ·
          {{ detail.session.modality || "-" }}
          ·
          {{ startedAtText }}{{ endedAtText !== "-" ? ` - ${endedAtText}` : "" }}
        </p>
      </div>

      <div class="light2-actions">
        <a v-if="playbackReady" :href="`/play/${detail.session.sessionId}`" target="_blank" class="light2-btn light2-btn-primary" style="text-decoration:none">数据回放</a>
        <BaseButton v-else variant="soft" tone="session" disabled title="当前数据不满足播放规则，请先执行处理任务">数据回放</BaseButton>
        <BaseButton :to="`/export?sessionId=${detail.session.sessionId}`" variant="secondary" tone="export">
          查看导出
        </BaseButton>
        <BaseButton :to="`/acquisition/${detail.session.taskId}`" variant="ghost" tone="session">返回任务</BaseButton>
      </div>
    </div>

    <div class="light2-metrics light2-detail-metrics">
      <div v-for="item in metricItems" :key="item.label" class="light2-mcard">
        <div class="light2-mstripe" :style="{ background: item.color }" />
        <div class="light2-mlabel">{{ item.label }}</div>
        <div class="light2-mvalue">{{ item.value }}</div>
        <div class="light2-mcap">{{ item.caption }}</div>
      </div>
    </div>

    <div class="light2-detail-grid">
      <div class="light2-detail-main">
        <section class="light2-panel">
          <div class="light2-panel-hdr">
            <span class="light2-panel-title">原始资产</span>
            <span class="light2-panel-sub">{{ rawAssets.length }} 资产</span>
          </div>

          <div v-if="!rawAssets.length" class="light2-empty-state">
            当前采集下还没有可展示的资产组，建议先确认上传结果或继续采集流程。
          </div>

          <div v-else>
            <article v-for="asset in rawAssets" :key="asset.id" class="light2-asset-row">
              <div class="light2-asset-row-icon">{{ assetGlyph(asset) }}</div>
              <div class="light2-asset-row-info">
                <div class="light2-asset-row-name">{{ asset.fileName || asset.assetName }}</div>
                <div class="light2-asset-row-meta">
                  <span>{{ asset.fileFormat || "-" }}</span>
                  <span>{{ asset.assetType || "-" }}</span>
                  <span>{{ formatFileSize(asset.fileSize) }}</span>
                </div>
              </div>
              <div class="light2-asset-row-actions">
                <BaseButton :to="`/data/${asset.id}?taskId=${detail.task?.id ?? detail.session.taskId}`" variant="ghost" tone="asset" size="sm">详情</BaseButton>
              </div>
            </article>
          </div>
        </section>

        <!-- 处理产物 -->
        <section class="light2-panel" v-for="pg in processedGroups" :key="'pg-'+pg.jobId">
          <div class="light2-panel-hdr">
            <span class="light2-panel-title">处理产物 · Job #{{ pg.jobId }} · {{ pg.pipelineId }}</span>
            <span class="light2-panel-sub">
              <span class="light2-badge" :class="pg.status==='SUCCESS'?'light2-badge-ok':pg.status==='FAILED'?'light2-badge-err':'light2-badge-warn'"><span class="light2-bdot" :style="{background:pg.status==='SUCCESS'?'#0d9444':pg.status==='FAILED'?'#d92d20':'#b87a0a'}"/>{{ pg.status }}</span>
              {{ pg.assets.length }} 个产物
            </span>
          </div>
          <article v-for="asset in pg.assets" :key="asset.id" class="light2-asset-row">
            <div class="light2-asset-row-icon">{{ assetGlyph(asset) }}</div>
            <div class="light2-asset-row-info">
              <div class="light2-asset-row-name">{{ asset.fileName || asset.assetName }}</div>
              <div class="light2-asset-row-meta">
                <span>{{ asset.fileFormat || "-" }}</span>
                <span>{{ asset.assetType || "-" }}</span>
                <span>{{ formatFileSize(asset.fileSize) }}</span>
              </div>
            </div>
            <div class="light2-asset-row-actions">
              <a v-if="showDownload(asset)" :href="asset.rawAsset.storageUrl||undefined" target="_blank" rel="noreferrer" class="light2-btn light2-btn-sec light2-btn-sm">下载</a>
              <BaseButton :to="`/data/${asset.id}?taskId=${detail.task?.id??detail.session.taskId}`" variant="ghost" tone="asset" size="sm">详情</BaseButton>
            </div>
          </article>
        </section>

        <section class="light2-panel">
          <div class="light2-panel-hdr">
            <span class="light2-panel-title">可用处理</span>
            <span class="light2-panel-sub">{{ availablePipelines.length }} 个可用规则</span>
          </div>
          <div v-if="!availablePipelines.length" class="light2-empty-state">该 Profile 下暂无可用处理规则，请先在「处理」页面创建规则并关联 Profile。</div>
          <div v-else class="light2-job-list">
            <article v-for="p in availablePipelines" :key="p.pipelineId" class="light2-job-row">
              <div class="light2-job-code">{{ p.pipelineId }}</div>
              <div class="light2-job-name">{{ p.displayName }}</div>
              <div class="light2-job-time" style="color:var(--color-text-tertiary);font-size:12px">{{ p.executorType }}</div>
              <div class="light2-job-duration"></div>
              <div class="light2-job-status">
                <button class="light2-btn light2-btn-primary light2-btn-sm" @click="executePipeline(p)" :disabled="executingPipelineId === p.pipelineId">
                  {{ executingPipelineId === p.pipelineId ? '创建中...' : '执行' }}
                </button>
              </div>
            </article>
          </div>
          <p v-if="executeError" style="color:#d92d20;font-size:12px;margin-top:8px">{{ executeError }}</p>
        </section>

        <section class="light2-panel">
          <div class="light2-panel-hdr">
            <span class="light2-panel-title">关联处理作业</span>
            <span class="light2-panel-sub">{{ sessionJobs.length }} 个作业</span>
          </div>

          <div v-if="!sessionJobs.length" class="light2-empty-state">
            当前采集尚未进入处理阶段。
          </div>

          <div v-else class="light2-job-list">
            <article v-for="job in sessionJobs" :key="job.id" class="light2-job-row">
              <div class="light2-job-code">{{ job.pipelineId || `JOB-${job.id}` }}</div>
              <div class="light2-job-name">{{ job.executorType || `处理作业 #${job.id}` }}</div>
              <div class="light2-job-time">{{ formatDateTime(latestTime(job.updatedAt, job.createdAt)) }}</div>
              <div class="light2-job-duration">{{ job.duration || '-' }}</div>
              <div class="light2-job-status">
                <StatusBadge :status="job.status" />
              </div>
            </article>
          </div>
        </section>
      </div>

      <aside class="light2-detail-side">
        <section class="light2-info-card">
          <div class="light2-info-card-hdr">采集信息</div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">采集编号</span><span class="light2-info-card-value light2-info-card-code">{{ detail.session.sessionCode || "-" }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">会话标识</span><span class="light2-info-card-value light2-info-card-code">{{ detail.session.sessionId }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">所属任务</span><RouterLink :to="`/acquisition/${detail.session.taskId}`" class="light2-info-card-link">{{ detail.task?.taskCode || `#${detail.session.taskId}` }}</RouterLink></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">任务名称</span><span class="light2-info-card-value">{{ detail.task?.taskName || detail.session.taskName || "-" }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">被试</span><span class="light2-info-card-value">{{ detail.session.subjectCode || "-" }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">动作</span><span class="light2-info-card-value">{{ detail.session.actionName || "-" }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">采集配置</span><span class="light2-info-card-value light2-info-card-code">{{ detail.session.profileName || detail.session.profileCode || "-" }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">模态</span><span class="light2-info-card-value">{{ detail.session.modality || "-" }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">时间同步/来源</span><span class="light2-info-card-value">{{ syncOrSourceLabel }}</span></div>
        </section>

        <section class="light2-info-card">
          <div class="light2-info-card-hdr">时间线</div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">开始时间</span><span class="light2-info-card-value light2-info-card-code">{{ startedAtText }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">结束时间</span><span class="light2-info-card-value light2-info-card-code">{{ endedAtText }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">持续时长</span><span class="light2-info-card-value light2-info-card-code">{{ sessionDurationText }}</span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">最近事件</span><span class="light2-info-card-value light2-info-card-code">{{ latestEventText }}</span></div>
          <div class="light2-timeline">
            <article v-for="item in timelineItems" :key="`${item.time}-${item.text}`" class="light2-timeline-item">
              <div class="light2-timeline-time">{{ item.time }}</div>
              <div class="light2-timeline-text">{{ item.text }}</div>
            </article>
          </div>
        </section>

        <section class="light2-info-card">
          <div class="light2-info-card-hdr">质检与导出</div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">QC 状态</span><span class="light2-info-card-value"><StatusBadge :status="detail.qcSummary.overallStatus" /></span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">导出状态</span><span class="light2-info-card-value"><StatusBadge :status="detail.session.exportStatus" /></span></div>
          <div class="light2-info-card-row"><span class="light2-info-card-label">上传状态</span><span class="light2-info-card-value"><StatusBadge :status="detail.session.uploadStatus" /></span></div>
          <div class="light2-info-card-progress">
            <div class="light2-info-card-progress-head">
              <span>文件就绪进度</span>
              <span class="light2-info-card-code">{{ uploadProgressText }}</span>
            </div>
            <div class="light2-progress">
              <div class="light2-progress-fill light2-progress-fill-green" :style="{ width: `${uploadProgressPercent}%` }"></div>
            </div>
            <div class="light2-info-card-progress-caption">{{ uploadProgressCaption }}</div>
          </div>
        </section>

        <section class="light2-info-card">
          <div class="light2-info-card-hdr">快捷操作</div>
          <div class="light2-quick-actions">
            <BaseButton :to="playbackReady ? `/play/${detail.session.sessionId}` : undefined" variant="soft" tone="session" block :disabled="!playbackReady" :title="playbackReady ? '' : '当前数据不满足播放规则'">数据回放</BaseButton>
            <BaseButton :to="`/export?sessionId=${detail.session.sessionId}`" variant="secondary" tone="export" block>
              导出数据
            </BaseButton>
            <BaseButton :to="`/qc?taskId=${detail.session.taskId}`" variant="secondary" tone="qc" block>
              查看 QC 报告
            </BaseButton>
          </div>
        </section>
      </aside>
    </div>
  </div>

  <div v-else class="light2-page">
    <div class="light2-empty-state">正在加载采集详情...</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { fetchSessionDetail } from "@/api/platform";
import { fetchAvailablePipelines } from "@/api/pipelines";
import { createSessionJob, fetchSessionJobs } from "@/api/processing";
import { checkSessionPlayback } from "@/api/sessions";
import BaseButton from "@/components/BaseButton.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { AssetListItem, SessionDataGroup, SessionDetailViewModel } from "@/types/platform";
import type { PipelineDefinitionResponse } from "@/types/pipeline";
import type { ProcessingJobResponse } from "@/types/processing";
import { formatDateTime, formatFileSize, formatStatusLabel } from "@/utils/format";

const route = useRoute();
const detail = ref<SessionDetailViewModel | null>(null);
const expandedGroupKeys = ref<string[]>([]);
const assetPreviewLimit = 8;

// --- 可用处理 ---
const availablePipelines = ref<PipelineDefinitionResponse[]>([]);
const sessionJobs = ref<ProcessingJobResponse[]>([]);
const executingPipelineId = ref<string | null>(null);
const playbackReady = ref(false);            // Session 级别：原始+全部产物是否满足播放规则
const executeError = ref("");

async function loadProcessingData(sessionId: number) {
  try { availablePipelines.value = await fetchAvailablePipelines(sessionId); } catch { availablePipelines.value = []; }
  try { sessionJobs.value = await fetchSessionJobs(sessionId); } catch { sessionJobs.value = []; }
  const sid = detail.value?.session?.sessionId;
  if (sid) {
    try { playbackReady.value = await checkSessionPlayback(sid); } catch { playbackReady.value = false; }
  }
}

async function executePipeline(p: PipelineDefinitionResponse) {
  const sid = detail.value?.session?.sessionId;
  if (!sid) return;
  executingPipelineId.value = p.pipelineId;
  executeError.value = "";
  try {
    // sessionId 是数据库主键 id，从路由参数或 detail 中获取
    // 注意: detail.session.sessionId 是 session 的业务标识（如 fake_session_001_final），不是数据库主键
    // 需要通过 detail.session.id 获取主键
    await createSessionJob(detail.value!.session.id!, { pipelineId: p.pipelineId });
    await loadProcessingData(detail.value!.session.id!);
  } catch (e: any) {
    executeError.value = e?.message || "创建处理任务失败";
  } finally {
    executingPipelineId.value = null;
  }
}

const FAILURE_STATUSES = new Set(["FAILED", "ERROR", "QC_FAILED", "WARNING", "QC_WARNING"]);
const PENDING_STATUSES = new Set(["PENDING", "WAITING", "RUNNING", "UPLOADING", "CREATED"]);
const READY_STATUSES = new Set(["READY", "PASSED", "QC_PASSED", "SUCCESS", "PLAYABLE", "UPLOADED"]);

const sortedJobs = computed(() =>
  [...(detail.value?.jobs ?? [])].sort((left, right) =>
    latestTime(right.updatedAt, right.createdAt).localeCompare(latestTime(left.updatedAt, left.createdAt)),
  ),
);

// 原始资产（非处理产物）
const rawAssets = computed(() =>
  (detail.value?.assets ?? []).filter(a => !a.rawAsset?.producedByJobId)
);

// 处理产物按 Job 分组
const processedGroups = computed(() => {
  const all = detail.value?.assets ?? [];
  const jobs = detail.value?.jobs ?? [];
  const jobMap = new Map<number, ProcessingJobResponse>();
  for (const j of jobs) jobMap.set(j.id, j);

  const grouped = new Map<number, AssetListItem[]>();
  for (const a of all) {
    const jobId = a.rawAsset?.producedByJobId;
    if (jobId == null) continue;
    if (!grouped.has(jobId)) grouped.set(jobId, []);
    grouped.get(jobId)!.push(a);
  }
  return Array.from(grouped.entries()).map(([jobId, assets]) => {
    const job = jobMap.get(jobId);
    return {
      jobId,
      pipelineId: job?.pipelineId ?? `JOB-${jobId}`,
      status: job?.status ?? 'UNKNOWN',
      assets,
    };
  });
});

const sessionPrimaryStatus = computed(() => {
  if (!detail.value) {
    return "PENDING";
  }

  const statuses = [
    detail.value.session.exportStatus,
    detail.value.session.processingStatus,
    detail.value.session.uploadStatus,
    detail.value.session.dataStatus,
    detail.value.session.qcStatus,
  ];

  return (
    statuses.find((status) => FAILURE_STATUSES.has(status))
    || statuses.find((status) => PENDING_STATUSES.has(status))
    || statuses.find((status) => READY_STATUSES.has(status))
    || "PENDING"
  );
});

const sessionPrimaryLabel = computed(() => {
  if (!detail.value) {
    return "待处理";
  }
  if (FAILURE_STATUSES.has(sessionPrimaryStatus.value)) {
    return "异常待处理";
  }
  if (PENDING_STATUSES.has(sessionPrimaryStatus.value)) {
    return "处理中";
  }
  if (detail.value.session.exportStatus === "READY") {
    return "已可导出";
  }
  return formatStatusLabel(sessionPrimaryStatus.value);
});

const startedAtText = computed(() => formatDateTime(detail.value?.session.startedAt || detail.value?.session.createdAt));
const endedAtText = computed(() => formatDateTime(detail.value?.session.endedAt || undefined));

const sessionDurationText = computed(() => {
  if (!detail.value) {
    return "-";
  }
  return durationLabel(detail.value.session.durationMs, detail.value.session.startedAt, detail.value.session.endedAt);
});

const totalSize = computed(() =>
  detail.value?.groups.reduce((sum, group) => sum + (group.totalSize || 0), 0) ?? 0,
);

const totalFileSizeLabel = computed(() => formatFileSize(totalSize.value));

const metricItems = computed(() => [
  {
    label: "数据资产",
    value: detail.value?.assets.length ?? 0,
    caption: `${detail.value?.groups.length ?? 0} 个资产组`,
    color: "var(--color-brand-500)",
  },
  {
    label: "文件总数",
    value: detail.value?.session.fileCount ?? 0,
    caption: "全部文件已纳入会话",
    color: "#0d9444",
  },
  {
    label: "采集时长",
    value: sessionDurationText.value,
    caption: `${detail.value?.session.durationMs ?? 0} ms`,
    color: "#7c3aed",
  },
  {
    label: "数据总量",
    value: totalFileSizeLabel.value,
    caption: "按资产组聚合",
    color: "#0d8ea0",
  },
]);

const uploadReadyCount = computed(() => {
  if (!detail.value) {
    return 0;
  }
  return detail.value.assets.filter(
    (asset) =>
      Boolean(asset.uploadedAt)
      || Boolean(asset.rawAsset.storageUrl),
  ).length;
});

const uploadProgressDenominator = computed(() => Math.max(detail.value?.assets.length ?? 0, 1));
const uploadProgressPercent = computed(() => Math.min(100, Math.round((uploadReadyCount.value / uploadProgressDenominator.value) * 100)));
const uploadProgressText = computed(() => `${uploadReadyCount.value}/${detail.value?.assets.length ?? 0}`);
const uploadProgressCaption = computed(() => `按可访问/已上传资产占比计算，不改变现有后端口径。`);

const latestEventText = computed(() => {
  if (!detail.value) {
    return "-";
  }
  const times = [
    detail.value.session.endedAt,
    ...detail.value.assets.map((asset) => asset.uploadedAt),
    ...detail.value.reports.map((report) => report.createdAt),
    ...detail.value.jobs.map((job) => latestTime(job.updatedAt, job.createdAt)),
  ]
    .filter(Boolean)
    .sort((left, right) => String(right).localeCompare(String(left)));
  return formatDateTime(String(times[0] || ""));
});

const syncOrSourceLabel = computed(() => {
  if (!detail.value) {
    return "-";
  }
  return (
    metadataValue(["时间同步", "同步策略", "time sync", "同步"])
    || detail.value.session.sourceSummary
    || "-"
  );
});

const timelineItems = computed(() => {
  if (!detail.value) {
    return [];
  }

  const items = [
    detail.value.session.startedAt || detail.value.session.createdAt
      ? {
          time: shortTime(detail.value.session.startedAt || detail.value.session.createdAt),
          text: "采集开始 · 会话登记",
        }
      : null,
    detail.value.session.endedAt
      ? {
          time: shortTime(detail.value.session.endedAt),
          text: "采集结束 · 数据封装",
        }
      : null,
    uploadReadyCount.value > 0
      ? {
          time: shortTime(latestAssetUploadedAt()),
          text: `上传完成 · ${detail.value.assets.length} 资产 / ${detail.value.session.fileCount ?? 0} 文件`,
        }
      : null,
    detail.value.reports.length
      ? {
          time: shortTime(latestReportCreatedAt()),
          text: `QC ${formatStatusLabel(detail.value.qcSummary.overallStatus)} · 已生成报告`,
        }
      : null,
    detail.value.session.exportStatus === "READY"
      ? {
          time: shortTime(latestEventText.value),
          text: "导出可用 · 可继续交付",
        }
      : null,
  ].filter((item): item is { time: string; text: string } => Boolean(item));

  return items.slice(0, 5);
});

function metadataValue(candidates: string[]) {
  const lookup = candidates.map((item) => item.toLowerCase());
  const found = detail.value?.metadata.find((item) => lookup.some((candidate) => item.label.toLowerCase().includes(candidate)));
  return found?.value || "";
}

function latestAssetUploadedAt() {
  const latest = (detail.value?.assets ?? [])
    .map((asset) => asset.uploadedAt || "")
    .sort((left, right) => right.localeCompare(left))[0];
  return latest || detail.value?.session.createdAt || "";
}

function latestReportCreatedAt() {
  const latest = (detail.value?.reports ?? [])
    .map((report) => report.createdAt || "")
    .sort((left, right) => right.localeCompare(left))[0];
  return latest || detail.value?.session.createdAt || "";
}

function shortTime(value?: string | null) {
  const text = formatDateTime(value || undefined);
  return text === "-" ? "-" : text.slice(-5);
}

function latestTime(primary?: string | null, fallback?: string | null) {
  return primary || fallback || "";
}

function durationLabel(durationMs?: number | null, startedAt?: string | null, endedAt?: string | null) {
  let ms = durationMs ?? null;
  if ((ms == null || Number.isNaN(ms)) && startedAt && endedAt) {
    const start = new Date(startedAt).getTime();
    const end = new Date(endedAt).getTime();
    if (!Number.isNaN(start) && !Number.isNaN(end) && end >= start) {
      ms = end - start;
    }
  }

  if (ms == null || Number.isNaN(ms)) {
    return "-";
  }

  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes <= 0) {
    return `${seconds}s`;
  }
  return `${minutes}m ${seconds}s`;
}

function groupGlyph(group: SessionDataGroup) {
  const title = `${group.title} ${group.assetTypes.join(" ")}`.toUpperCase();
  if (title.includes("RGB") || title.includes("VIDEO") || title.includes("MP4")) return "VI";
  if (title.includes("IMU")) return "IM";
  if (title.includes("SMPL")) return "SM";
  if (title.includes("ZIP") || title.includes("ARCHIVE")) return "AR";
  if (title.includes("DOC") || title.includes("TEXT")) return "DO";
  return "AS";
}

function assetGlyph(asset: AssetListItem) {
  const format = String(asset.fileFormat || "").toUpperCase();
  const type = String(asset.assetType || "").toUpperCase();
  if (format.includes("MP4") || format.includes("MOV") || type.includes("VIDEO")) return "VI";
  if (format.includes("CSV") || type.includes("IMU")) return "CSV";
  if (format.includes("ZIP")) return "ZIP";
  if (format.includes("NPZ")) return "NPZ";
  if (format.includes("JSON")) return "JS";
  return format.slice(0, 3) || "FI";
}

function groupHint(group: SessionDataGroup) {
  const issues = group.assets.filter((asset) => FAILURE_STATUSES.has(asset.qcStatus)).length;
  if (issues > 0) {
    return `${issues} 个异常项`;
  }
  if (group.assets.some((asset) => PENDING_STATUSES.has(asset.processingStatus))) {
    return "存在处理中资产";
  }
  if (group.assets.some((asset) => asset.sourceType === "derived")) {
    return "包含派生结果";
  }
  return "";
}

function groupPrimaryStatus(group: SessionDataGroup) {
  if (group.assets.some((asset) => FAILURE_STATUSES.has(asset.qcStatus) || FAILURE_STATUSES.has(asset.processingStatus))) {
    return "QC_FAILED";
  }
  if (group.assets.some((asset) => PENDING_STATUSES.has(asset.qcStatus) || PENDING_STATUSES.has(asset.processingStatus))) {
    return "PENDING";
  }
  if (group.assets.some((asset) => Boolean(asset.rawAsset.storageUrl) || asset.deliverableStatus === "READY")) {
    return "READY";
  }
  return "SUCCESS";
}

function groupStatusLabel(group: SessionDataGroup) {
  const status = groupPrimaryStatus(group);
  if (status === "QC_FAILED") return "存在异常";
  if (status === "PENDING") return "处理中";
  if (status === "READY") return "已就绪";
  return "状态稳定";
}

function isPlayableAsset(asset: AssetListItem) {
  const format = String(asset.fileFormat || "").toLowerCase();
  const type = String(asset.assetType || "").toLowerCase();
  return ["mp4", "mov", "avi", "mkv", "webm"].some((item) => format.includes(item)) || type.includes("video");
}

function showDownload(asset: AssetListItem) {
  return Boolean(asset.rawAsset.storageUrl) && !isPlayableAsset(asset);
}

function isGroupExpanded(key: string) {
  return expandedGroupKeys.value.includes(key);
}

function expandGroup(key: string) {
  if (!isGroupExpanded(key)) {
    expandedGroupKeys.value = [...expandedGroupKeys.value, key];
  }
}

function visibleAssets(group: SessionDataGroup) {
  if (group.assets.length <= assetPreviewLimit || isGroupExpanded(group.key)) {
    return group.assets;
  }
  return group.assets.slice(0, assetPreviewLimit);
}

async function loadDetail() {
  detail.value = await fetchSessionDetail(String(route.params.sessionId));
  expandedGroupKeys.value = [];
  if (detail.value?.session?.id) {
    loadProcessingData(detail.value.session.id);
  }
}

watch(
  () => route.params.sessionId,
  () => {
    void loadDetail();
  },
);

onMounted(() => {
  void loadDetail();
});
</script>
