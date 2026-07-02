<template>
  <div v-if="detail" class="light2-page">
    <div class="light2-hdr">
      <div class="min-w-0">
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <span class="light2-badge light2-badge-neutral">
            <span class="light2-bdot" style="background: #9298a3" />
            任务详情
          </span>
          <span class="light2-badge" :class="badgeClass(detail.task.status)">
            <span class="light2-bdot" :style="{ background: badgeColor(detail.task.status) }" />
            {{ formatStatusLabel(detail.task.status) }}
          </span>
        </div>
        <h1>{{ detail.task.taskName }}</h1>
        <p>
          {{ detail.task.taskCode || `#${detail.task.id}` }}
          路
          {{ detail.task.subjectCode || "-" }}
          路
          {{ detail.task.actionName || "-" }}
          路
          {{ detail.task.profileName || "-" }}
          路
          {{ detail.task.collectDate || "-" }}
        </p>
      </div>

      <div class="light2-actions">
        <RouterLink :to="`/upload?taskId=${detail.task.id}`" class="light2-btn light2-btn-primary">
          上传数据
        </RouterLink>
        <RouterLink :to="`/sessions?taskId=${detail.task.id}`" class="light2-btn light2-btn-sec">
          查看采集
        </RouterLink>
        <RouterLink :to="`/acquisition/${detail.task.id}/dag`" class="light2-btn" style="background:#17181a;color:#fff;border-color:#17181a;">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" style="margin-right:2px;">
            <circle cx="4" cy="4" r="2" fill="currentColor" opacity="0.8"/>
            <circle cx="12" cy="4" r="2" fill="currentColor" opacity="0.8"/>
            <circle cx="8" cy="12" r="2" fill="currentColor" opacity="0.8"/>
            <line x1="5.5" y1="5" x2="7.5" y2="10.5" stroke="currentColor" stroke-width="1.2" opacity="0.6"/>
            <line x1="10.5" y1="5" x2="8.5" y2="10.5" stroke="currentColor" stroke-width="1.2" opacity="0.6"/>
          </svg>
          数据链路图
        </RouterLink>
      </div>
    </div>

    <div class="light2-metrics">
      <div v-for="m in metricItems" :key="m.label" class="light2-mcard">
        <div class="light2-mstripe" :style="{ background: metricColor(m.tone) }" />
        <div class="light2-mlabel">{{ m.label }}</div>
        <div class="light2-mvalue">{{ m.value }}</div>
        <div class="light2-tsub mt-2">{{ m.caption }}</div>
      </div>
    </div>

    <div class="light2-tbl overflow-x-auto">
      <table class="min-w-[1420px]">
        <thead>
          <tr>
            <th>采集</th>
            <th>所属任务</th>
            <th>数据总大小</th>
            <th>时长</th>
            <th>被试 - 动作</th>
            <th>采集员</th>
            <th>上传时间</th>
            <th>配置</th>
            <th>资产 / 文件</th>
            <th style="width: 140px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!sortedSessions.length">
            <td colspan="10" style="text-align: center; padding: 48px 0; color: var(--color-text-secondary)">
              当前任务还没有采集会话，建议先上传数据或进入采集流程。
            </td>
          </tr>
          <tr v-for="(session, index) in sortedSessions" :key="session.sessionId">
            <td>
              <div class="flex flex-wrap items-center gap-2">
                <RouterLink :to="`/sessions/${session.sessionId}`" class="light2-code">
                  {{ session.sessionCode || session.sessionId }}
                </RouterLink>
                <span v-if="index === 0" class="light2-badge light2-badge-info">
                  <span class="light2-bdot" style="background: var(--color-brand-500)" />
                  最新采集
                </span>
              </div>
              <div v-if="session.sessionCode && session.sessionCode !== session.sessionId" class="light2-tsub">{{ session.sessionId }}</div>
            </td>
            <td>
              <RouterLink :to="`/acquisition/${session.taskId}`" class="light2-tname hover:underline">{{ session.taskName || "-" }}</RouterLink>
              <div class="light2-tsub">{{ session.taskCode || `#${session.taskId}` }}</div>
            </td>
            <td>{{ formatFileSize(session.totalSize || 0) }}</td>
            <td>-</td>
            <td>{{ session.subjectCode || "-" }} - {{ session.actionName || "-" }}</td>
            <td>{{ session.collectorName || "-" }}</td>
            <td>{{ formatDateTime(session.uploadedAt ?? undefined) }}</td>
            <td>{{ session.profileName || session.profileCode || "-" }}</td>
            <td class="light2-code">{{ session.assetCount ?? 0 }}/{{ session.fileCount ?? 0 }}</td>
            <td>
              <div class="light2-actions">
                <RouterLink :to="`/play/${session.sessionId}`" class="light2-btn light2-btn-sec light2-btn-sm">播放</RouterLink>
                <RouterLink :to="`/sessions/${session.sessionId}`" class="light2-btn light2-btn-sec light2-btn-sm">详情</RouterLink>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="grid gap-4 xl:grid-cols-3">
      <section class="rounded-[16px] border border-[var(--color-border-soft)] bg-white px-4 py-4 shadow-[var(--shadow-card)]">
        <div class="mb-2 text-xs font-semibold tracking-[0.08em] text-[var(--color-text-tertiary)] uppercase">下一步建议</div>
        <div class="space-y-3">
          <div v-for="action in nextActionSummaries" :key="action.title" class="rounded-[12px] bg-[var(--color-surface-page)] px-3 py-3">
            <div class="text-sm font-medium text-[var(--color-text-primary)]">{{ action.title }}</div>
            <div class="mt-1 text-xs leading-5 text-[var(--color-text-secondary)]">{{ action.description }}</div>
            <RouterLink :to="action.to" class="mt-2 inline-flex text-xs font-medium text-[var(--color-brand-600)] hover:underline">
              {{ action.cta }}
            </RouterLink>
          </div>
        </div>
      </section>

      <section class="rounded-[16px] border border-[var(--color-border-soft)] bg-white px-4 py-4 shadow-[var(--shadow-card)]">
        <div class="mb-2 text-xs font-semibold tracking-[0.08em] text-[var(--color-text-tertiary)] uppercase">处理摘要</div>
        <div v-if="processingSummaries.length" class="space-y-3">
          <div v-for="item in processingSummaries" :key="item.title" class="rounded-[12px] bg-[var(--color-surface-page)] px-3 py-3">
            <div class="flex items-center justify-between gap-3">
              <div class="text-sm font-medium text-[var(--color-text-primary)]">{{ item.title }}</div>
              <span class="light2-badge" :class="badgeClass(item.status)">
                <span class="light2-bdot" :style="{ background: badgeColor(item.status) }" />
                {{ formatStatusLabel(item.status) }}
              </span>
            </div>
            <div class="mt-1 text-xs text-[var(--color-text-secondary)]">{{ item.subtitle }}</div>
            <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ item.caption }}</div>
          </div>
        </div>
        <div v-else class="text-sm text-[var(--color-text-secondary)]">当前任务尚未进入处理阶段。</div>
      </section>

      <section class="rounded-[16px] border border-[var(--color-border-soft)] bg-white px-4 py-4 shadow-[var(--shadow-card)]">
        <div class="mb-2 text-xs font-semibold tracking-[0.08em] text-[var(--color-text-tertiary)] uppercase">元数据</div>
        <div class="space-y-3">
          <div v-for="item in metadataItems" :key="item.label" class="rounded-[12px] bg-[var(--color-surface-page)] px-3 py-3">
            <div class="text-xs text-[var(--color-text-tertiary)]">{{ item.label }}</div>
            <div class="mt-1 text-sm font-medium text-[var(--color-text-primary)] break-all">{{ item.value }}</div>
          </div>
        </div>
      </section>
    </div>
  </div>

  <div v-else class="light2-page">
    <div class="light2-tbl">
      <div style="text-align: center; padding: 64px 0; color: var(--color-text-secondary)">正在加载任务详情...</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { fetchAcquisitionDetail } from "@/api/platform";
import type { AcquisitionDetailViewModel } from "@/types/platform";
import { formatDateTime, formatFileSize, formatStatusLabel } from "@/utils/format";

const route = useRoute();
const detail = ref<AcquisitionDetailViewModel | null>(null);

const FAILURE_STATUSES = new Set(["FAILED", "ERROR", "QC_FAILED", "WARNING", "QC_WARNING"]);
const READY_STATUSES = new Set(["READY", "PASSED", "QC_PASSED", "SUCCESS", "PLAYABLE"]);

const BADGE_MAP: Record<string, { cls: string; color: string }> = {
  ACTIVE: { cls: "light2-badge-info", color: "var(--color-brand-500)" },
  CREATED: { cls: "light2-badge-neutral", color: "#9298a3" },
  COMPLETED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  ERROR: { cls: "light2-badge-err", color: "#c5222f" },
  FAILED: { cls: "light2-badge-err", color: "#c5222f" },
  QC_PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  QC_WARNING: { cls: "light2-badge-warn", color: "#b87a0a" },
  QC_FAILED: { cls: "light2-badge-err", color: "#c5222f" },
  PENDING: { cls: "light2-badge-neutral", color: "#9298a3" },
  RUNNING: { cls: "light2-badge-warn", color: "#b87a0a" },
  UPLOADING: { cls: "light2-badge-info", color: "var(--color-brand-500)" },
  UPLOADED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  SUCCESS: { cls: "light2-badge-ok", color: "#0d7d3e" },
  READY: { cls: "light2-badge-ok", color: "#0d7d3e" },
  PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  WARNING: { cls: "light2-badge-warn", color: "#b87a0a" },
};

const sortedSessions = computed(() =>
  [...(detail.value?.sessions ?? [])].sort((left, right) =>
    latestTime(right.startedAt, right.createdAt).localeCompare(latestTime(left.startedAt, left.createdAt)),
  ),
);

const abnormalSessions = computed(() =>
  sortedSessions.value.filter((session) =>
    [session.qcStatus, session.exportStatus, session.processingStatus, session.uploadStatus].some((status) =>
      FAILURE_STATUSES.has(status),
    ),
  ),
);

const readyExportCount = computed(() => sortedSessions.value.filter((session) => session.exportStatus === "READY").length);

const metricItems = computed(() => {
  const assets = detail.value?.assets ?? [];
  return [
    {
      label: "采集数",
      value: sortedSessions.value.length,
      caption: "当前任务下已识别的 Session 数量",
      tone: "session",
    },
    {
      label: "资产数",
      value: assets.length,
      caption: "当前任务已登记的全部资产",
      tone: "asset",
    },
    {
      label: "异常采集",
      value: abnormalSessions.value.length,
      caption: abnormalSessions.value.length ? "建议优先排查异常会话" : "当前未发现异常会话",
      tone: "qc",
    },
    {
      label: "可导出采集",
      value: readyExportCount.value,
      caption: readyExportCount.value ? "已具备导出条件" : "暂无 READY 采集",
      tone: "export",
    },
  ];
});

const nextActionSummaries = computed(() => {
  if (!detail.value) {
    return [];
  }

  const firstAbnormal = abnormalSessions.value[0];
  const firstReady = sortedSessions.value.find((session) => READY_STATUSES.has(session.exportStatus));

  if (!sortedSessions.value.length) {
    return [
      {
        title: "先上传数据",
        description: "当前任务还没有采集会话，建议先进入上传页接入普通文件或标准 Session 目录。",
        cta: "去上传数据",
        to: `/upload?taskId=${detail.value.task.id}`,
      },
      {
        title: "查看任务采集入口",
        description: "如果已经有外部采集计划，可以直接进入采集列表确认当前任务下是否已有会话。",
        cta: "查看采集列表",
        to: `/sessions?taskId=${detail.value.task.id}`,
      },
    ];
  }

  const actions = [
    firstAbnormal
      ? {
          title: "优先处理异常采集",
          description: "当前任务下存在状态异常的 Session，建议先进入对应采集详情排查质检、处理或导出问题。",
          cta: "查看异常采集",
          to: `/sessions/${firstAbnormal.sessionId}`,
        }
      : {
          title: "继续查看采集",
          description: "当前任务下已有采集会话，建议继续按采集维度核对资产、处理与质检进度。",
          cta: "进入采集列表",
          to: `/sessions?taskId=${detail.value.task.id}`,
        },
    firstReady
      ? {
          title: "查看可导出结果",
          description: "已有会话满足导出条件，可以继续进入采集详情或导出页确认交付物。",
          cta: "查看可导出采集",
          to: `/sessions/${firstReady.sessionId}`,
        }
      : {
          title: "补齐处理与质检",
          description: "当前还没有 READY 会话，建议先确认处理记录和质检状态是否已经完成。",
          cta: "查看处理模块",
          to: "/processing",
        },
  ];

  return actions;
});

const processingSummaries = computed(() =>
  [...(detail.value?.jobs ?? [])]
    .sort((left, right) => latestTime(right.updatedAt, right.createdAt).localeCompare(latestTime(left.updatedAt, left.createdAt)))
    .slice(0, 3)
    .map((job) => ({
      title: job.pipelineId || `处理作业 #${job.id}`,
      subtitle: `${job.executorType || "PROCESSING"} 路 作业 #${job.id}`,
      caption: `最近时间 ${formatDateTime(latestTime(job.updatedAt, job.createdAt))}${job.operatorName ? ` 路 操作人 ${job.operatorName}` : ""}`,
      status: job.status,
    })),
);

const metadataItems = computed(() => {
  const task = detail.value?.task;
  return [
    { label: "taskId", value: String(task?.id ?? "-") },
    { label: "taskCode", value: task?.taskCode || "-" },
    { label: "创建时间", value: formatDateTime(task?.createdAt) },
    { label: "创建人", value: task?.operatorName || "-" },
    { label: "备注", value: task?.remark || "-" },
  ];
});

function badgeClass(status: string) {
  return BADGE_MAP[status]?.cls ?? "light2-badge-neutral";
}

function badgeColor(status: string) {
  return BADGE_MAP[status]?.color ?? "#9298a3";
}

function metricColor(tone: string) {
  if (tone === "session") return "#0d9444";
  if (tone === "asset") return "#7c3aed";
  if (tone === "qc") return "#c5222f";
  if (tone === "export") return "#0d8ea0";
  return "var(--color-brand-500)";
}

function latestTime(primary?: string | null, fallback?: string | null) {
  return primary || fallback || "";
}

onMounted(async () => {
  detail.value = await fetchAcquisitionDetail(Number(route.params.taskId));
});
</script>
