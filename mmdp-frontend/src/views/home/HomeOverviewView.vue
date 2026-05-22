<template>
  <div class="space-y-4">
    <PageHeader eyebrow="主页" title="平台概览" description="查看任务、会话、数据、处理和质检的最新情况。" :meta="headerMeta" />

    <section class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
      <MetricCard label="采集任务" :value="overview.taskCount" />
      <MetricCard label="Session" :value="overview.sessionCount" />
      <MetricCard label="数据资产" :value="overview.assetCount" />
      <MetricCard label="质检通过率" :value="overview.qcPassRate" />
    </section>

    <div class="grid gap-4 xl:grid-cols-3">
      <PageCard title="最近会话">
        <div class="space-y-2.5">
          <div v-for="session in overview.recentSessions" :key="session.sessionId" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
            <div class="flex items-center justify-between gap-3">
              <div class="min-w-0">
                <div class="truncate text-sm font-medium text-slate-900">{{ session.sessionName }}</div>
                <div class="mt-1 text-xs text-slate-500">{{ session.sessionId }}</div>
              </div>
              <StatusBadge :status="session.qcStatus" />
            </div>
          </div>
        </div>
      </PageCard>

      <PageCard title="最近数据">
        <div class="space-y-2.5">
          <div v-for="asset in overview.recentAssets" :key="asset.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
            <div class="flex items-center justify-between gap-3">
              <RouterLink :to="`/data/${asset.id}?taskId=${asset.taskId}`" class="truncate text-sm font-medium text-slate-900 hover:text-[var(--color-brand-600)]">{{ asset.assetName }}</RouterLink>
              <StatusBadge :status="asset.qcStatus" />
            </div>
            <div class="mt-1 text-xs text-slate-500">{{ asset.assetType }} / {{ asset.fileFormat }}</div>
          </div>
        </div>
      </PageCard>

      <PageCard title="最近处理">
        <div class="space-y-2.5">
          <div v-for="job in overview.recentJobs" :key="job.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
            <div class="flex items-center justify-between gap-3">
              <div class="truncate text-sm font-medium text-slate-900">{{ job.pipelineId }}</div>
              <StatusBadge :status="job.status" />
            </div>
            <div class="mt-1 text-xs text-slate-500">{{ formatDateTime(job.createdAt) }}</div>
          </div>
        </div>
      </PageCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from "vue";
import { RouterLink } from "vue-router";
import { fetchPlatformOverview } from "@/api/platform";
import MetricCard from "@/components/MetricCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { PlatformOverview } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const overview = reactive<PlatformOverview>({
  taskCount: 0,
  sessionCount: 0,
  assetCount: 0,
  processingCount: 0,
  qcPassRate: "0%",
  recentSessions: [],
  recentAssets: [],
  recentJobs: []
});

const headerMeta = computed(() => [
  { label: "处理中", value: overview.processingCount },
  { label: "最近会话", value: overview.recentSessions.length }
]);

onMounted(async () => Object.assign(overview, await fetchPlatformOverview()));
</script>
