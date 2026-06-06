<template>
  <div v-if="detail" class="space-y-5">
    <PageHeader
      eyebrow="采集 Workspace"
      :title="detail.session.sessionCode || detail.session.sessionId"
      description="采集页聚焦当前采集的数据资产、质检和导出状态。"
      surface="plain"
    >
      <template #actions>
        <div class="flex flex-wrap items-center justify-end gap-2">
          <StatusBadge :status="detail.session.qcStatus" />
          <BaseButton :to="`/acquisition/${detail.session.taskId}`" variant="ghost">返回任务</BaseButton>
          <BaseButton variant="soft" tone="session" :to="`/play/${detail.session.sessionId}`">播放</BaseButton>
          <BaseButton :to="`/export?sessionId=${detail.session.sessionId}`">查看导出</BaseButton>
        </div>
      </template>
    </PageHeader>

    <WorkspaceOverviewBar :items="overviewItems" :secondary="overviewSecondary" />

    <div class="grid gap-3 xl:grid-cols-[minmax(0,1.2fr)_minmax(260px,0.8fr)]">
      <section class="workspace-section-secondary">
        <div class="mb-2 text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">核心健康状态</div>
        <WorkspaceHealthSummary :items="compactHealthItems" compact />
      </section>

      <section class="workspace-section-secondary">
        <div class="mb-2 text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">进度状态</div>
        <WorkflowTimeline :stages="sessionStages" compact />
      </section>
    </div>

    <BusinessMetrics :items="metricItems" />

    <PageCard eyebrow="核心工作区" title="Asset Groups" description="这是采集页唯一核心区，先看资产组，再决定是否下钻文件。">
      <div v-if="detail.groups.length" class="workspace-group-grid">
        <article
          v-for="group in detail.groups"
          :key="group.key"
          class="workspace-group-row app-tone-asset"
        >
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="min-w-0 flex-1 space-y-2">
              <div class="flex flex-wrap items-center gap-2">
                <div class="app-summary-title-strong">{{ group.title }}</div>
                <span class="rounded-full border border-[var(--module-asset-soft-border)] bg-[var(--module-asset-soft-bg)] px-2 py-0.5 text-[11px] font-medium text-[var(--module-asset-soft-text)]">
                  {{ groupStatusLabel(group) }}
                </span>
              </div>
              <div class="app-summary-subtitle-muted">{{ group.assetTypes.join(" / ") || "未标注资产类型" }}</div>
              <div class="app-summary-stat-inline">
                <span>资产 <strong>{{ group.assetCount }}</strong></span>
                <span>文件 <strong>{{ group.fileCount }}</strong></span>
                <span>大小 <strong>{{ formatFileSize(group.totalSize) }}</strong></span>
                <span>更新 <strong>{{ groupUpdatedAt(group) }}</strong></span>
              </div>
              <div class="app-summary-meta-inline">
                <span>健康 <strong>{{ groupHealthCaption(group) }}</strong></span>
                <span>·</span>
                <span>下一步 <strong>{{ nextStepForGroup(group) }}</strong></span>
              </div>
            </div>

            <div class="flex min-w-[180px] flex-col items-start gap-2 xl:items-end">
              <div class="app-status-stack">
                <StatusBadge :status="groupPrimaryStatus(group)" />
              </div>
              <div class="app-action-group">
                <BaseButton
                  size="sm"
                  variant="soft"
                  tone="asset"
                  @click="toggleGroup(group.key)"
                >
                  {{ activeGroupKey === group.key ? "收起文件" : "查看文件" }}
                </BaseButton>
                <BaseButton
                  v-if="group.assets[0]"
                  size="sm"
                  variant="ghost"
                  :to="`/data/${group.assets[0].id}?taskId=${detail.task?.id ?? detail.session.taskId}`"
                >
                  查看资产
                </BaseButton>
                <BaseButton
                  v-if="group.assets[0]"
                  size="sm"
                  variant="ghost"
                  :to="`/data/${group.assets[0].id}?taskId=${detail.task?.id ?? detail.session.taskId}`"
                >
                  详情
                </BaseButton>
              </div>
            </div>
          </div>

          <div v-if="activeGroupKey === group.key" class="mt-4 workspace-file-stack">
            <article
              v-for="asset in group.assets"
              :key="asset.id"
              class="workspace-file-row"
            >
              <div class="flex min-w-0 items-start gap-3">
                <div class="app-metric-icon mt-0.5" :class="fileToneClass(asset)">
                  <BaseIcon :name="fileIcon(asset)" size="sm" />
                </div>
                <div class="min-w-0">
                  <div class="truncate text-sm font-semibold text-[var(--color-text-primary)]">{{ asset.fileName || asset.assetName }}</div>
                  <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">
                    {{ asset.fileFormat || asset.assetType }} · {{ formatFileSize(asset.fileSize) }}
                  </div>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <StatusBadge :status="asset.qcStatus" />
                <BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${detail.task?.id ?? detail.session.taskId}`">详情</BaseButton>
              </div>
            </article>
          </div>
        </article>
      </div>
      <div v-else class="workspace-muted-empty">当前采集下还没有可分组展示的资产。</div>
    </PageCard>

    <div class="grid gap-3 xl:grid-cols-2">
      <section class="workspace-section-secondary">
        <div class="mb-3 flex items-center justify-between gap-3">
          <div>
            <div class="text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">QC Summary</div>
            <div class="mt-1 text-sm font-semibold text-[var(--color-text-primary)]">质检摘要</div>
          </div>
          <BaseButton size="sm" variant="ghost" :to="`/qc?taskId=${detail.session.taskId}`">查看报告</BaseButton>
        </div>
        <WorkspaceHealthSummary :items="qcItems" compact />
      </section>

      <section class="workspace-section-secondary">
        <div class="mb-3 flex items-center justify-between gap-3">
          <div>
            <div class="text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">Export Summary</div>
            <div class="mt-1 text-sm font-semibold text-[var(--color-text-primary)]">导出摘要</div>
          </div>
          <BaseButton size="sm" variant="ghost" :to="`/export?sessionId=${detail.session.sessionId}`">查看导出</BaseButton>
        </div>
        <WorkspaceHealthSummary :items="exportItems" compact />
      </section>
    </div>

    <div class="grid gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <section class="workspace-section-secondary">
        <div class="mb-3 text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">下一步</div>
        <NextActionsPanel :actions="nextActions" />
      </section>

      <div class="space-y-3">
        <WorkspaceDisclosure title="Related Processing" :summary="processingSummary">
          <RelatedProcessingPanel
            :items="processingItems"
            empty-text="当前采集尚未进入处理阶段。"
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
import { fetchSessionDetail } from "@/api/platform";
import type { MetricItem } from "@/components/BusinessMetrics.vue";
import BusinessMetrics from "@/components/BusinessMetrics.vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
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
import type { AssetListItem, SessionDataGroup, SessionDetailViewModel } from "@/types/platform";
import { formatDateTime, formatFileSize, formatStatusLabel } from "@/utils/format";

const route = useRoute();
const detail = ref<SessionDetailViewModel | null>(null);
const activeGroupKey = ref<string | null>(null);

const FAILURE_STATUSES = new Set(["FAILED", "ERROR", "QC_FAILED", "WARNING", "QC_WARNING"]);
const PENDING_STATUSES = new Set(["PENDING", "WAITING", "RUNNING", "UPLOADING", "CREATED"]);

const downloadableAssets = computed(() => detail.value?.assets.filter((asset) => asset.rawAsset.storageUrl) ?? []);
const activeGroup = computed(() => detail.value?.groups.find((group) => group.key === activeGroupKey.value) ?? null);

const overviewItems = computed(() => {
  if (!detail.value) {
    return [];
  }
  return [
    { label: "所属任务", value: detail.value.task?.taskCode || detail.value.session.taskName || detail.value.session.taskId },
    { label: "被试", value: detail.value.session.subjectCode || "-" },
    { label: "动作", value: detail.value.session.actionName || "-" },
    { label: "Profile", value: detail.value.session.profileName || detail.value.session.profileCode || "-" },
    { label: "采集时间", value: formatDateTime(detail.value.session.startedAt || detail.value.session.createdAt) },
  ];
});

const overviewSecondary = computed(() => {
  if (!detail.value) {
    return "";
  }
  return `Session 编号 ${detail.value.session.sessionCode || detail.value.session.sessionId} · 来源摘要 ${detail.value.session.sourceSummary || "暂无"} · 创建时间 ${formatDateTime(detail.value.session.createdAt)}`;
});

const abnormalCount = computed(() => {
  if (!detail.value) {
    return 0;
  }
  const reportCount = detail.value.reports.filter((report) => FAILURE_STATUSES.has(report.qcStatus)).length;
  const assetCount = detail.value.assets.filter((asset) => FAILURE_STATUSES.has(asset.qcStatus)).length;
  return Math.max(reportCount, assetCount, detail.value.qcSummary.overallStatus === "QC_FAILED" ? 1 : 0);
});

const pendingCount = computed(() => {
  if (!detail.value) {
    return 0;
  }
  return detail.value.assets.filter((asset) => PENDING_STATUSES.has(asset.qcStatus) || PENDING_STATUSES.has(asset.processingStatus)).length;
});

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
      label: "QC 状态",
      value: formatStatusLabel(detail.value?.qcSummary.overallStatus),
      caption: "当前采集质检摘要",
      icon: "clipboard-check",
      tone: "qc" as const,
    },
    {
      label: "可导出状态",
      value: formatStatusLabel(detail.value?.session.exportStatus),
      caption: downloadableAssets.value.length ? "已有导出结果" : "导出未就绪",
      icon: "download",
      tone: "export" as const,
    },
  ];

  if (abnormalCount.value > 0) {
    items.unshift({
      label: "异常数量",
      value: abnormalCount.value,
      caption: "存在需优先处理的异常项",
      icon: "shield-alert",
      tone: "qc" as const,
    });
  }

  if (pendingCount.value > 0) {
    items.push({
      label: "待处理项",
      value: pendingCount.value,
      caption: "仍有资产或文件待处理",
      icon: "clock",
      tone: "process" as const,
    });
  }

  return items.slice(0, 4);
});

const sessionStages = computed<
  Array<{ label: string; caption: string; status: WorkflowStageStatus; tone?: "session" | "asset" | "upload" | "qc" | "export" }>
>(() => {
  const hasGroups = (detail.value?.groups.length ?? 0) > 0;
  const hasFiles = (detail.value?.session.fileCount ?? 0) > 0;
  const qcStatus = detail.value?.qcSummary.overallStatus || "PENDING";
  const exportReady = detail.value?.session.exportStatus === "READY";
  const hasRisk = abnormalCount.value > 0;

  return [
    { label: "已登记", caption: "采集会话已建立", status: "done" as const, tone: "session" as const },
    {
      label: "资产组",
      caption: hasGroups ? `${detail.value?.groups.length ?? 0} 个分组` : "等待分组",
      status: (hasGroups ? "done" : "current") as WorkflowStageStatus,
      tone: "asset" as const,
    },
    {
      label: "文件可用",
      caption: hasFiles ? `${detail.value?.session.fileCount ?? 0} 个文件` : "等待文件",
      status: (hasFiles ? "done" : hasGroups ? "current" : "waiting") as WorkflowStageStatus,
      tone: "upload" as const,
    },
    {
      label: "QC",
      caption: detail.value?.reports.length ? `${detail.value?.reports.length ?? 0} 条结果` : "等待质检",
      status: (FAILURE_STATUSES.has(qcStatus) ? "risk" : detail.value?.reports.length ? "done" : "waiting") as WorkflowStageStatus,
      tone: "qc" as const,
    },
    {
      label: "导出",
      caption: exportReady ? `${downloadableAssets.value.length} 个结果可导出` : "尚未就绪",
      status: (exportReady ? "done" : hasRisk ? "risk" : "waiting") as WorkflowStageStatus,
      tone: "export" as const,
    },
  ];
});

const metricItems = computed<MetricItem[]>(() => [
  { label: "资产数", value: detail.value?.assets.length ?? 0, caption: "当前采集已登记的全部资产", icon: "database", tone: "asset" },
  { label: "文件数", value: detail.value?.session.fileCount ?? 0, caption: "关联的物理文件总数", icon: "file", tone: "upload" },
  { label: "总大小", value: formatFileSize(detail.value?.groups.reduce((sum, group) => sum + group.totalSize, 0) ?? 0), caption: "基于资产组总大小聚合", icon: "hard-drive", tone: "session" },
  { label: "异常数量", value: abnormalCount.value, caption: "基于质检与资产状态推导", icon: "shield-alert", tone: "qc" },
]);

const qcItems = computed(() => [
  {
    label: "QC 状态",
    value: formatStatusLabel(detail.value?.qcSummary.overallStatus),
    caption: detail.value?.qcSummary.note || "当前暂无更多质检说明。",
    icon: "clipboard-check",
    tone: "qc" as const,
  },
  {
    label: "最近 QC 时间",
    value: formatDateTime(detail.value?.reports[0]?.createdAt),
    caption: detail.value?.reports.length ? "基于最近一条质检报告。" : "暂无质检报告。",
    icon: "clock",
    tone: "qc" as const,
  },
  {
    label: "异常数量",
    value: abnormalCount.value,
    caption: "汇总当前采集中的失败或告警结果。",
    icon: "shield-alert",
    tone: "qc" as const,
  },
]);

const exportItems = computed(() => [
  {
    label: "导出状态",
    value: formatStatusLabel(detail.value?.session.exportStatus),
    caption: "基于当前采集和可下载资产推导。",
    icon: "download",
    tone: "export" as const,
  },
  {
    label: "可下载格式",
    value: downloadableAssets.value.length
      ? downloadableAssets.value
          .map((asset) => asset.fileFormat || asset.assetType)
          .filter((value, index, array) => array.indexOf(value) === index)
          .join(" / ")
      : "-",
    caption: "由当前可下载资产推导格式集合。",
    icon: "archive",
    tone: "export" as const,
  },
  {
    label: "最近导出时间",
    value: exportUpdatedAt.value,
    caption: "缺少结构化字段时，以可下载资产创建时间近似。",
    icon: "clock",
    tone: "export" as const,
  },
]);

const exportUpdatedAt = computed(() => {
  const latest = downloadableAssets.value
    .map((asset) => asset.uploadedAt)
    .sort((left, right) => right.localeCompare(left))[0];
  return formatDateTime(latest);
});

const processingItems = computed(() =>
  [...(detail.value?.jobs ?? [])]
    .sort((left, right) => latestTime(right.updatedAt, right.createdAt).localeCompare(latestTime(left.updatedAt, left.createdAt)))
    .slice(0, 3)
    .map((job) => ({
      title: job.pipelineId || `处理作业 #${job.id}`,
      subtitle: `作业 #${job.id} · ${job.executorType || "PROCESSING"}`,
      caption: `最近时间 ${formatDateTime(latestTime(job.updatedAt, job.createdAt))}`,
      status: job.status,
      to: "/processing",
    })),
);

const processingSummary = computed(() => {
  if (processingItems.value.length) {
    const latest = processingItems.value[0];
    return `${processingItems.value.length} 条处理记录 · 最近 ${latest.status ? formatStatusLabel(latest.status) : "已更新"}`;
  }
  return "当前采集尚未进入处理阶段";
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
    tone?: "task" | "session" | "process" | "qc" | "export" | "upload" | "asset";
  }> = [];

  if (FAILURE_STATUSES.has(detail.value.qcSummary.overallStatus) || abnormalCount.value > 0) {
    actions.push({
      label: "查看 QC 报告",
      description: "当前采集存在质检异常或告警，建议先查看报告并定位问题资产。",
      to: `/qc?taskId=${detail.value.session.taskId}`,
      cta: "查看报告",
      primary: true,
      tone: "qc",
    });
  } else if (detail.value.session.processingStatus === "PLAYABLE" || detail.value.session.dataStatus === "READY") {
    actions.push({
      label: "播放采集",
      description: "当前采集已具备回放价值，适合先进入播放视图做内容确认。",
      to: `/play/${detail.value.session.sessionId}`,
      cta: "播放采集",
      primary: true,
      tone: "session",
    });
  } else if (detail.value.session.exportStatus === "READY") {
    actions.push({
      label: "查看导出",
      description: "当前采集已有可导出的结果，建议继续确认导出内容。",
      to: `/export?sessionId=${detail.value.session.sessionId}`,
      cta: "查看导出",
      primary: true,
      tone: "export",
    });
  } else if (activeGroup.value?.assets[0]) {
    actions.push({
      label: "查看文件",
      description: "当前已定位到资产组，继续查看该组文件可以更快发现问题。",
      to: `/data/${activeGroup.value.assets[0].id}?taskId=${detail.value.session.taskId}`,
      cta: "查看文件",
      primary: true,
      tone: "asset",
    });
  } else {
    actions.push({
      label: "查看资产组",
      description: "先从资产组层面理解当前采集产出了什么，再决定是否下钻文件。",
      to: `/sessions/${detail.value.session.sessionId}`,
      cta: "查看资产组",
      primary: true,
      tone: "asset",
    });
  }

  actions.push({
    label: "返回任务",
    description: "回到任务工作台查看该采集在更大任务上下文中的位置。",
    to: `/acquisition/${detail.value.session.taskId}`,
    cta: "返回任务",
    tone: "task",
  });

  if (processingItems.value.length) {
    actions.push({
      label: "查看处理结果",
      description: "当前采集已有处理作业，可继续进入处理工作区追踪结果。",
      to: "/processing",
      cta: "查看处理",
      tone: "process",
    });
  }

  return actions.slice(0, 3);
});

const metadataItems = computed(() => {
  const extra = detail.value?.metadata ?? [];
  const base = [
    { label: "sessionId", value: detail.value?.session.sessionId || "-" },
    { label: "创建时间", value: formatDateTime(detail.value?.session.createdAt) },
    { label: "创建人", value: detail.value?.session.operatorName || "-" },
  ];
  return [...base, ...extra.filter((item) => !["sessionId", "创建时间", "创建人"].includes(item.label))];
});

const metadataSummary = computed(() => {
  return `Session ${detail.value?.session.sessionCode || detail.value?.session.sessionId || "-"} · 创建时间 ${formatDateTime(detail.value?.session.createdAt)}`;
});

function latestTime(primary?: string | null, fallback?: string | null) {
  return primary || fallback || "";
}

function toggleGroup(key: string) {
  activeGroupKey.value = activeGroupKey.value === key ? null : key;
}

function groupAssetsWithIssues(group: SessionDataGroup) {
  return group.assets.filter((asset) => FAILURE_STATUSES.has(asset.qcStatus)).length;
}

function groupStatusLabel(group: SessionDataGroup) {
  const failed = group.assets.some((asset) => FAILURE_STATUSES.has(asset.qcStatus));
  if (failed) {
    return "存在异常";
  }
  const pending = group.assets.some((asset) => PENDING_STATUSES.has(asset.qcStatus) || PENDING_STATUSES.has(asset.processingStatus));
  if (pending) {
    return "待处理";
  }
  return "状态稳定";
}

function groupHealthCaption(group: SessionDataGroup) {
  const issues = groupAssetsWithIssues(group);
  return issues ? `${issues} 个异常项` : "未见明显异常";
}

function groupUpdatedAt(group: SessionDataGroup) {
  const latest = group.assets
    .map((asset) => asset.uploadedAt)
    .sort((left, right) => right.localeCompare(left))[0];
  return formatDateTime(latest);
}

function nextStepForGroup(group: SessionDataGroup) {
  if (groupAssetsWithIssues(group)) {
    return "优先检查文件";
  }
  if (group.assets.some((asset) => PENDING_STATUSES.has(asset.processingStatus))) {
    return "继续等待处理";
  }
  return "可继续查看导出或播放";
}

function groupPrimaryStatus(group: SessionDataGroup) {
  if (group.assets.some((asset) => FAILURE_STATUSES.has(asset.qcStatus))) {
    return "QC_FAILED";
  }
  if (group.assets.some((asset) => PENDING_STATUSES.has(asset.qcStatus) || PENDING_STATUSES.has(asset.processingStatus))) {
    return "PENDING";
  }
  return "READY";
}

function fileIcon(asset: AssetListItem) {
  const format = String(asset.fileFormat || "").toLowerCase();
  if (format === "mp4" || format === "mov" || asset.assetType.includes("VIDEO")) {
    return "camera";
  }
  if (format === "csv") {
    return "sheet";
  }
  if (format === "zip" || asset.assetType.includes("ARCHIVE")) {
    return "archive";
  }
  if (format === "json" || format === "txt" || format === "md") {
    return "file-text";
  }
  return "file";
}

function fileToneClass(asset: AssetListItem) {
  const format = String(asset.fileFormat || "").toLowerCase();
  if (format === "mp4" || format === "mov" || asset.assetType.includes("VIDEO")) {
    return "app-tone-session";
  }
  if (format === "csv") {
    return "app-tone-process";
  }
  if (format === "zip" || asset.assetType.includes("ARCHIVE")) {
    return "app-tone-export";
  }
  return "app-tone-asset";
}

onMounted(async () => {
  detail.value = await fetchSessionDetail(String(route.params.sessionId));
  activeGroupKey.value = detail.value?.groups[0]?.key ?? null;
});
</script>
