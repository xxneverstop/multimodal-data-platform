<template>
  <div v-if="detail" class="space-y-5">
    <PageHeader
      eyebrow="采集 Workspace"
      :title="detail.session.sessionCode || detail.session.sessionId"
      description="围绕采集阶段、资产组、质检、导出和下一步动作组织的工作台。"
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

    <PageCard eyebrow="健康摘要" title="当前采集是否健康" description="快速查看当前采集的质检、异常、待处理和导出就绪情况。">
      <WorkspaceHealthSummary :items="healthItems" />
    </PageCard>

    <PageCard eyebrow="阶段进度" title="采集当前处于什么阶段" description="沿着采集、资产组、文件、质检和导出的主线理解当前工作进度。">
      <WorkflowTimeline :stages="sessionStages" />
    </PageCard>

    <section class="space-y-3">
      <div>
        <div class="text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">规模概览</div>
        <h2 class="mt-1 text-[17px] font-semibold text-[var(--color-text-primary)]">采集产出规模</h2>
      </div>
      <BusinessMetrics :items="metricItems" />
    </section>

    <PageCard eyebrow="核心产出" title="Asset Groups" description="先看当前采集产出的资产组，再决定是否下钻到具体文件。">
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
              </div>
              <div class="app-summary-meta-inline">
                <span>健康 <strong>{{ groupHealthCaption(group) }}</strong></span>
                <span>·</span>
                <span>最近更新 <strong>{{ groupUpdatedAt(group) }}</strong></span>
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

    <PageCard eyebrow="文件" title="Files" description="文件默认不作为首屏主体，先选择资产组，再查看该组的文件与明细。">
      <div v-if="activeGroup" class="space-y-3">
        <div class="rounded-[14px] border border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-4 py-3 text-sm text-[var(--color-text-secondary)]">
          当前正在查看 <span class="font-semibold text-[var(--color-text-primary)]">{{ activeGroup.title }}</span> 下的文件，共 {{ activeGroup.assets.length }} 条。
        </div>
        <div class="workspace-file-stack">
          <article
            v-for="asset in activeGroup.assets"
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
      </div>
      <div v-else class="workspace-muted-empty">先在上方 Asset Groups 中选择一个资产组，再查看该组的文件列表。</div>
    </PageCard>

    <PageCard eyebrow="质检" title="QC" description="单独查看当前采集的质检状态、异常数量、最近报告和查看入口。">
      <div class="space-y-4">
        <WorkspaceHealthSummary :items="qcItems" />
        <div v-if="detail.reports.length" class="space-y-2">
          <article
            v-for="report in detail.reports.slice(0, 3)"
            :key="report.id"
            class="workspace-secondary-row"
          >
            <div class="min-w-0">
              <div class="text-sm font-semibold text-[var(--color-text-primary)]">报告 #{{ report.id }}</div>
              <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ report.summary }}</div>
            </div>
            <div class="flex items-center gap-2">
              <StatusBadge :status="report.qcStatus" />
              <BaseButton size="sm" variant="ghost" :to="`/qc?taskId=${detail.session.taskId}`">查看报告</BaseButton>
            </div>
          </article>
        </div>
        <div v-else class="workspace-muted-empty">当前无可用 QC 结果，可在下一步动作中继续查看质检入口或等待新的检查结果。</div>
      </div>
    </PageCard>

    <PageCard eyebrow="导出" title="Export" description="统一查看导出状态、可下载格式和导出入口，而不是把导出文件直接暴露成主体列表。">
      <div class="space-y-4">
        <WorkspaceHealthSummary :items="exportItems" />
        <div v-if="downloadableAssets.length" class="space-y-2">
          <article
            v-for="asset in downloadableAssets.slice(0, 5)"
            :key="asset.id"
            class="workspace-secondary-row"
          >
            <div class="min-w-0">
              <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ asset.assetName }}</div>
              <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ asset.fileFormat || asset.assetType }} · {{ formatFileSize(asset.fileSize) }}</div>
            </div>
            <div class="flex items-center gap-2">
              <StatusBadge :status="detail.session.exportStatus" />
              <BaseButton size="sm" variant="ghost" :href="asset.rawAsset.storageUrl || undefined">下载</BaseButton>
            </div>
          </article>
        </div>
        <div v-else class="workspace-muted-empty">当前采集尚无可直接下载的导出文件。</div>
      </div>
    </PageCard>

    <PageCard eyebrow="相关处理" title="Related Processing" description="查看该采集是否已经进入处理链路，以及最近发生了哪些处理作业。">
      <RelatedProcessingPanel :items="processingItems" empty-text="当前采集尚未进入处理阶段。" />
    </PageCard>

    <PageCard eyebrow="下一步" title="Next Actions" description="根据当前采集的健康度、导出状态和可播放状态，给出最值得执行的下一步。">
      <NextActionsPanel :actions="nextActions" />
    </PageCard>

    <PageCard eyebrow="元数据" title="Metadata" description="基础记录信息下沉到底部，避免与资产组、质检、导出等主工作区争夺注意力。" secondary>
      <WorkspaceMetadataGrid :items="metadataItems" />
    </PageCard>
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

const healthItems = computed(() => [
  {
    label: "QC 状态",
    value: formatStatusLabel(detail.value?.qcSummary.overallStatus),
    caption: "基于当前采集的 Session 级质检摘要。",
    icon: "clipboard-check",
    tone: "qc" as const,
  },
  {
    label: "异常数量",
    value: abnormalCount.value,
    caption: abnormalCount.value ? "存在需优先处理的异常或质检失败项。" : "当前未发现明显异常。",
    icon: "shield-alert",
    tone: "qc" as const,
  },
  {
    label: "待处理项",
    value: pendingCount.value,
    caption: pendingCount.value ? "仍有资产或文件处于待处理状态。" : "当前待处理项较少。",
    icon: "clock",
    tone: "process" as const,
  },
  {
    label: "可导出状态",
    value: formatStatusLabel(detail.value?.session.exportStatus),
    caption: downloadableAssets.value.length ? `已有 ${downloadableAssets.value.length} 个可下载导出结果。` : "当前尚无可直接下载的导出结果。",
    icon: "download",
    tone: "export" as const,
  },
]);

const sessionStages = computed<
  Array<{ label: string; caption: string; status: WorkflowStageStatus; tone?: "session" | "asset" | "upload" | "qc" | "export" }>
>(() => {
  const hasGroups = (detail.value?.groups.length ?? 0) > 0;
  const hasFiles = (detail.value?.session.fileCount ?? 0) > 0;
  const qcStatus = detail.value?.qcSummary.overallStatus || "PENDING";
  const exportReady = detail.value?.session.exportStatus === "READY";
  const hasRisk = abnormalCount.value > 0;

  return [
    { label: "Session Registered", caption: "采集会话已登记", status: "done" as const, tone: "session" as const },
    {
      label: "Assets Grouped",
      caption: hasGroups ? `${detail.value?.groups.length ?? 0} 个资产组` : "等待形成资产组",
      status: (hasGroups ? "done" : "current") as WorkflowStageStatus,
      tone: "asset" as const,
    },
    {
      label: "Files Available",
      caption: hasFiles ? `${detail.value?.session.fileCount ?? 0} 个文件已关联` : "等待文件登记",
      status: (hasFiles ? "done" : hasGroups ? "current" : "waiting") as WorkflowStageStatus,
      tone: "upload" as const,
    },
    {
      label: "QC Reviewed",
      caption: detail.value?.reports.length ? `${detail.value?.reports.length ?? 0} 条质检记录` : "等待质检结果",
      status: (FAILURE_STATUSES.has(qcStatus) ? "risk" : detail.value?.reports.length ? "done" : "waiting") as WorkflowStageStatus,
      tone: "qc" as const,
    },
    {
      label: "Export Ready",
      caption: exportReady ? `${downloadableAssets.value.length} 个结果可导出` : "尚未形成导出结果",
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
    label: "QC 总状态",
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
  {
    label: "查看入口",
    value: detail.value?.reports.length ? "可查看报告" : "暂无报告",
    caption: "更多细节可进入质检工作台查看。",
    icon: "search",
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
  {
    label: "导出入口",
    value: downloadableAssets.value.length ? "可进入导出" : "暂不可用",
    caption: "统一通过导出工作区查看下载入口。",
    icon: "download",
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
