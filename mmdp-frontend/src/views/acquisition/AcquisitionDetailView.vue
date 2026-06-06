<template>
  <div v-if="detail" class="space-y-5">
    <PageHeader
      eyebrow="任务 Workspace"
      :title="detail.task.taskName"
      description="任务页聚焦管理当前任务下的采集进展。"
      surface="plain"
    >
      <template #actions>
        <div class="flex flex-wrap items-center justify-end gap-2">
          <StatusBadge :status="detail.task.status" />
          <BaseButton variant="soft" tone="task" :to="`/upload?taskId=${detail.task.id}`">上传数据</BaseButton>
          <BaseButton :to="`/sessions?taskId=${detail.task.id}`">查看采集</BaseButton>
        </div>
      </template>
    </PageHeader>

    <WorkspaceOverviewBar :items="overviewItems" :secondary="overviewSecondary" />

    <div class="grid gap-3 xl:grid-cols-[minmax(0,1.25fr)_minmax(260px,0.75fr)]">
      <section class="workspace-section-secondary">
        <div class="mb-2 text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">进度状态</div>
        <WorkflowTimeline :stages="taskStages" compact />
      </section>

      <section class="workspace-section-secondary">
        <div class="mb-2 text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">健康提示</div>
        <WorkspaceHealthSummary :items="compactHealthItems" compact />
      </section>
    </div>

    <BusinessMetrics :items="metricItems" />

    <PageCard eyebrow="核心工作区" title="Session Workspace" description="这是任务页唯一核心区，直接管理这个任务下的采集进展。">
      <div v-if="sortedSessions.length" class="space-y-2">
        <article
          v-for="(session, index) in sortedSessions"
          :key="session.sessionId"
          class="workspace-core-row app-row-accent-task"
        >
          <div class="min-w-0 flex-1 space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <div class="app-summary-title-strong">{{ session.sessionCode || session.sessionId }}</div>
              <span
                v-if="index === 0"
                class="rounded-full border border-[var(--module-task-soft-border)] bg-[var(--module-task-soft-bg)] px-2 py-0.5 text-[11px] font-medium text-[var(--module-task-soft-text)]"
              >
                最近更新
              </span>
              <span
                class="rounded-full border border-[var(--module-task-soft-border)] bg-[var(--module-task-soft-bg)] px-2 py-0.5 text-[11px] font-medium text-[var(--module-task-soft-text)]"
              >
                {{ sessionHealthLabel(session) }}
              </span>
            </div>
            <div class="app-summary-subtitle-muted">{{ session.sourceSummary || session.modality || "暂无数据类型摘要" }}</div>
            <div class="app-summary-meta-inline">
              <span>被试 <strong>{{ session.subjectCode || "-" }}</strong></span>
              <span>·</span>
              <span>动作 <strong>{{ session.actionName || "-" }}</strong></span>
              <span>·</span>
              <span>Profile <strong>{{ session.profileName || session.profileCode || "-" }}</strong></span>
            </div>
            <div class="app-summary-stat-inline">
              <span>资产 <strong>{{ session.assetCount }}</strong></span>
              <span>文件 <strong>{{ session.fileCount }}</strong></span>
              <span>更新 {{ sessionUpdatedAt(session) }}</span>
            </div>
          </div>

          <div class="flex min-w-[180px] flex-col items-start gap-2 xl:items-end">
            <div class="app-status-stack">
              <StatusBadge :status="session.exportStatus" />
              <StatusBadge :status="session.qcStatus" />
              <StatusBadge :status="session.processingStatus" />
            </div>
            <div class="app-action-group">
              <BaseButton size="sm" variant="soft" tone="task" :to="`/sessions/${session.sessionId}`">查看采集</BaseButton>
              <BaseButton size="sm" variant="ghost" :to="`/play/${session.sessionId}`">播放</BaseButton>
              <BaseButton size="sm" variant="ghost" :to="`/sessions/${session.sessionId}`">详情</BaseButton>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="workspace-muted-empty">当前任务尚未产出采集会话，建议先上传数据或进入采集流程。</div>
    </PageCard>

    <div class="grid gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <section class="workspace-section-secondary">
        <div class="mb-3 text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">下一步</div>
        <NextActionsPanel :actions="nextActions" />
      </section>

      <div class="space-y-3">
        <WorkspaceDisclosure title="Related Processing" :summary="processingSummary">
          <RelatedProcessingPanel
            :items="processingItems"
            empty-text="当前任务尚未进入处理阶段。"
          />
        </WorkspaceDisclosure>

        <WorkspaceDisclosure title="Metadata" :summary="metadataSummary">
          <WorkspaceMetadataGrid :items="metadataItems" />
        </WorkspaceDisclosure>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchAcquisitionDetail } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import BusinessMetrics, { type MetricItem } from "@/components/BusinessMetrics.vue";
import NextActionsPanel from "@/components/NextActionsPanel.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import RelatedProcessingPanel from "@/components/RelatedProcessingPanel.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import WorkflowTimeline, { type WorkflowStageStatus } from "@/components/WorkflowTimeline.vue";
import WorkspaceDisclosure from "@/components/WorkspaceDisclosure.vue";
import WorkspaceHealthSummary from "@/components/WorkspaceHealthSummary.vue";
import WorkspaceMetadataGrid from "@/components/WorkspaceMetadataGrid.vue";
import WorkspaceOverviewBar from "@/components/WorkspaceOverviewBar.vue";
import type { AcquisitionDetailViewModel, SessionRecord } from "@/types/platform";
import { formatDateTime, formatStatusLabel } from "@/utils/format";

const route = useRoute();
const detail = ref<AcquisitionDetailViewModel | null>(null);

const FAILURE_STATUSES = new Set(["FAILED", "ERROR", "QC_FAILED", "WARNING", "QC_WARNING"]);
const PENDING_STATUSES = new Set(["PENDING", "WAITING", "RUNNING", "UPLOADING", "CREATED"]);
const READY_STATUSES = new Set(["READY", "PASSED", "QC_PASSED", "SUCCESS", "PLAYABLE"]);

const sortedSessions = computed(() =>
  [...(detail.value?.sessions ?? [])].sort((left, right) =>
    latestTime(right.startedAt, right.createdAt).localeCompare(latestTime(left.startedAt, left.createdAt)),
  ),
);

const overviewItems = computed(() => {
  const task = detail.value?.task;
  return [
    { label: "被试", value: task?.subjectCode },
    { label: "动作", value: task?.actionName },
    { label: "Profile", value: task?.profileName || "-" },
    { label: "日期", value: task?.collectDate || "-" },
  ];
});

const overviewSecondary = computed(() => {
  const task = detail.value?.task;
  if (!task) {
    return "";
  }
  return `任务编号 ${task.taskCode || task.id} · 创建时间 ${formatDateTime(task.createdAt)} · 创建人 ${task.operatorName || "-"}`;
});

const abnormalSessions = computed(() =>
  sortedSessions.value.filter((session) =>
    [session.qcStatus, session.exportStatus, session.processingStatus, session.uploadStatus].some((status) =>
      FAILURE_STATUSES.has(status),
    ),
  ),
);

const pendingSessions = computed(() =>
  sortedSessions.value.filter((session) =>
    [session.qcStatus, session.exportStatus, session.processingStatus, session.uploadStatus].some((status) =>
      PENDING_STATUSES.has(status),
    ),
  ),
);

const latestReportStatus = computed(() => detail.value?.reports?.[0]?.qcStatus || detail.value?.task.status || "PENDING");
const readyExportCount = computed(() => sortedSessions.value.filter((session) => session.exportStatus === "READY").length);

const compactHealthItems = computed<
  Array<{
    label: string;
    value: string | number;
    caption: string;
    icon: string;
    tone: "qc" | "export" | "process";
  }>
>(() => {
  const items: Array<{
    label: string;
    value: string | number;
    caption: string;
    icon: string;
    tone: "qc" | "export" | "process";
  }> = [
    {
      label: "最近 QC",
      value: formatStatusLabel(latestReportStatus.value),
      caption: "基于最近一次质检状态",
      icon: "clipboard-check",
      tone: "qc" as const,
    },
    {
      label: "导出可用",
      value: `${readyExportCount.value}/${sortedSessions.value.length || 0}`,
      caption: readyExportCount.value ? "已有可导出采集" : "暂无 READY 采集",
      icon: "download",
      tone: "export" as const,
    },
  ];

  if (abnormalSessions.value.length > 0) {
    items.unshift({
      label: "异常采集",
      value: abnormalSessions.value.length,
      caption: "建议优先排查异常采集",
      icon: "shield-alert",
      tone: "qc" as const,
    });
  }

  if (pendingSessions.value.length > 0) {
    items.push({
      label: "待处理采集",
      value: pendingSessions.value.length,
      caption: "仍有采集处于待处理阶段",
      icon: "clock",
      tone: "process" as const,
    });
  }

  return items.slice(0, 4);
});

const taskStages = computed<
  Array<{ label: string; caption: string; status: WorkflowStageStatus; tone?: "task" | "session" | "asset" | "process" | "qc" | "export" }>
>(() => {
  const hasSessions = sortedSessions.value.length > 0;
  const hasAssets = (detail.value?.assets.length ?? 0) > 0;
  const hasJobs = (detail.value?.jobs.length ?? 0) > 0;
  const runningJobs = detail.value?.jobs.some((job) => PENDING_STATUSES.has(job.status)) ?? false;
  const hasReports = (detail.value?.reports.length ?? 0) > 0;
  const hasRisk = abnormalSessions.value.length > 0;
  const exportReady = readyExportCount.value > 0;

  return [
    { label: "任务已创建", caption: "工作容器已建立", status: "done" as const, tone: "task" as const },
    {
      label: "采集进入",
      caption: hasSessions ? `${sortedSessions.value.length} 个采集` : "等待采集",
      status: (hasSessions ? "done" : "current") as WorkflowStageStatus,
      tone: "session" as const,
    },
    {
      label: "资产登记",
      caption: hasAssets ? `${detail.value?.assets.length ?? 0} 个资产` : "等待资产进入",
      status: (hasAssets ? "done" : hasSessions ? "current" : "waiting") as WorkflowStageStatus,
      tone: "asset" as const,
    },
    {
      label: "处理链路",
      caption: hasJobs ? `${detail.value?.jobs.length ?? 0} 条记录` : "暂无处理",
      status: (runningJobs ? "current" : hasJobs ? "done" : "waiting") as WorkflowStageStatus,
      tone: "process" as const,
    },
    {
      label: "质检",
      caption: hasReports ? `${detail.value?.reports.length ?? 0} 条结果` : "等待质检",
      status: (hasRisk ? "risk" : hasReports ? "done" : "waiting") as WorkflowStageStatus,
      tone: "qc" as const,
    },
    {
      label: "导出",
      caption: exportReady ? `${readyExportCount.value} 个可导出` : "尚未就绪",
      status: (exportReady ? "done" : hasRisk ? "risk" : "waiting") as WorkflowStageStatus,
      tone: "export" as const,
    },
  ];
});

const metricItems = computed<MetricItem[]>(() => {
  const assets = detail.value?.assets ?? [];
  const lastUpdated = latestTimestamp([
    detail.value?.task.createdAt,
    ...sortedSessions.value.map((session) => latestTime(session.startedAt, session.createdAt)),
    ...(detail.value?.jobs ?? []).map((job) => latestTime(job.updatedAt, job.createdAt)),
    ...(detail.value?.reports ?? []).map((report) => report.createdAt),
  ]);

  return [
    { label: "采集数", value: sortedSessions.value.length, caption: "当前任务下已识别的 Session 数量", icon: "camera", tone: "session" },
    { label: "资产数", value: assets.length, caption: "当前任务已登记的全部资产", icon: "database", tone: "asset" },
    { label: "最近更新时间", value: lastUpdated || "-", caption: "基于任务、采集、处理和质检时间推导", icon: "clock", tone: "task" },
  ];
});

const processingItems = computed(() =>
  [...(detail.value?.jobs ?? [])]
    .sort((left, right) => latestTime(right.updatedAt, right.createdAt).localeCompare(latestTime(left.updatedAt, left.createdAt)))
    .slice(0, 3)
    .map((job) => ({
      title: job.pipelineId || `处理作业 #${job.id}`,
      subtitle: `作业 #${job.id} · ${job.executorType || "PROCESSING"}`,
      caption: `最近时间 ${formatDateTime(latestTime(job.updatedAt, job.createdAt))}${job.operatorName ? ` · 操作人 ${job.operatorName}` : ""}`,
      status: job.status,
      to: "/processing",
    })),
);

const processingSummary = computed(() => {
  if (processingItems.value.length) {
    const latest = processingItems.value[0];
    return `${processingItems.value.length} 条处理记录 · 最近 ${latest.status ? formatStatusLabel(latest.status) : "已更新"}`;
  }
  return "当前任务尚未进入处理阶段";
});

const nextActions = computed(() => {
  if (!detail.value) {
    return [];
  }
  const actions: Array<{
    label: string;
    description: string;
    to: string;
    cta?: string;
    primary?: boolean;
    tone?: "task" | "session" | "process" | "qc" | "export" | "upload";
  }> = [];

  const firstAbnormal = abnormalSessions.value[0];
  const firstPending = pendingSessions.value[0];
  const firstPlayable = sortedSessions.value.find((session) => READY_STATUSES.has(session.exportStatus) || session.processingStatus === "PLAYABLE");

  if (!sortedSessions.value.length) {
    actions.push({
      label: "上传数据",
      description: "当前任务下还没有采集会话，先上传数据或发起采集是最合理的下一步。",
      to: `/upload?taskId=${detail.value.task.id}`,
      cta: "上传数据",
      primary: true,
      tone: "upload",
    });
  } else if (firstAbnormal) {
    actions.push({
      label: "查看异常采集",
      description: "当前任务下存在异常 Session，建议优先进入异常采集排查质检或导出问题。",
      to: `/sessions/${firstAbnormal.sessionId}`,
      cta: "进入异常采集",
      primary: true,
      tone: "qc",
    });
  } else if (firstPending) {
    actions.push({
      label: "继续查看采集",
      description: "仍有采集处于待处理阶段，进入该采集可以继续查看资产、质检和导出进度。",
      to: `/sessions/${firstPending.sessionId}`,
      cta: "继续查看",
      primary: true,
      tone: "session",
    });
  } else if (firstPlayable) {
    actions.push({
      label: "播放最近采集",
      description: "当前已有可直接进入的采集结果，适合继续做回放和内容确认。",
      to: `/play/${firstPlayable.sessionId}`,
      cta: "播放采集",
      primary: true,
      tone: "session",
    });
  } else {
    actions.push({
      label: "查看全部采集",
      description: "进入完整采集列表，继续确认每个 Session 的状态与产出。",
      to: `/sessions?taskId=${detail.value.task.id}`,
      cta: "查看采集",
      primary: true,
      tone: "task",
    });
  }

  actions.push({
    label: "查看采集列表",
    description: "按采集工作台方式继续查看该任务下的全部会话。",
    to: `/sessions?taskId=${detail.value.task.id}`,
    cta: "查看采集",
    tone: "task",
  });

  if (processingItems.value.length) {
    actions.push({
      label: "查看相关处理",
      description: "已有处理作业记录，可继续进入处理模块追踪结果。",
      to: "/processing",
      cta: "查看处理",
      tone: "process",
    });
  }

  return actions.slice(0, 3);
});

const metadataItems = computed(() => {
  const task = detail.value?.task;
  return [
    { label: "taskId", value: task?.id ?? "-" },
    { label: "taskCode", value: task?.taskCode || "-" },
    { label: "创建时间", value: formatDateTime(task?.createdAt) },
    { label: "创建人", value: task?.operatorName || "-" },
    { label: "备注", value: task?.remark || "-" },
  ];
});

const metadataSummary = computed(() => {
  const task = detail.value?.task;
  return `任务编号 ${task?.taskCode || task?.id || "-"} · 创建时间 ${formatDateTime(task?.createdAt)}`;
});

function latestTime(primary?: string | null, fallback?: string | null) {
  return primary || fallback || "";
}

function latestTimestamp(values: Array<string | undefined>) {
  return values
    .filter((value): value is string => Boolean(value))
    .sort((left, right) => right.localeCompare(left))[0]
    ? formatDateTime(
        values
          .filter((value): value is string => Boolean(value))
          .sort((left, right) => right.localeCompare(left))[0],
      )
    : "";
}

function sessionUpdatedAt(session: SessionRecord) {
  return formatDateTime(latestTime(session.startedAt, session.createdAt));
}

function sessionHealthLabel(session: SessionRecord) {
  if ([session.qcStatus, session.exportStatus, session.processingStatus, session.uploadStatus].some((status) => FAILURE_STATUSES.has(status))) {
    return "异常";
  }
  if ([session.qcStatus, session.exportStatus, session.processingStatus, session.uploadStatus].some((status) => PENDING_STATUSES.has(status))) {
    return "待处理";
  }
  return "正常";
}

onMounted(async () => {
  detail.value = await fetchAcquisitionDetail(Number(route.params.taskId));
});
</script>
