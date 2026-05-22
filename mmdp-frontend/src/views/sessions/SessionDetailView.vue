<template>
  <div v-if="detail" class="space-y-4">
    <PageHeader eyebrow="功能 / Session" :title="detail.session.sessionName" description="查看当前会话下的资产、处理和质检。" :meta="headerMeta">
      <template #actions>
        <BaseButton :to="`/upload?taskId=${detail.session.taskId}&sessionId=${detail.session.sessionId}`">新建</BaseButton>
        <BaseButton variant="primary" :to="`/data?taskId=${detail.session.taskId}&sessionId=${detail.session.sessionId}`">查看数据</BaseButton>
      </template>
    </PageHeader>

    <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
      <EntitySummaryCard label="taskId" :value="detail.session.taskId" />
      <EntitySummaryCard label="sessionId" :value="detail.session.sessionId" />
      <EntitySummaryCard label="资产数" :value="detail.assets.length" />
      <EntitySummaryCard label="处理记录" :value="detail.jobs.length" />
    </div>

    <SectionTabs v-model="activeTab" :items="tabs" />

    <PageCard v-if="activeTab === 'assets'" title="数据">
      <div class="grid gap-4 xl:grid-cols-2">
        <div>
          <h3 class="mb-2 text-sm font-medium text-slate-900">采集数据</h3>
          <div class="space-y-2.5">
            <div v-for="asset in inputAssets" :key="asset.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
              <div class="flex items-center justify-between gap-3">
                <BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${asset.taskId}`">{{ asset.assetName }}</BaseButton>
                <StatusBadge :status="asset.qcStatus" />
              </div>
            </div>
          </div>
        </div>
        <div>
          <h3 class="mb-2 text-sm font-medium text-slate-900">派生数据</h3>
          <div class="space-y-2.5">
            <div v-for="asset in derivedAssets" :key="asset.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
              <div class="flex items-center justify-between gap-3">
                <BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${asset.taskId}`">{{ asset.assetName }}</BaseButton>
                <StatusBadge :status="asset.processingStatus" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </PageCard>

    <PageCard v-else-if="activeTab === 'jobs'" title="处理记录">
      <div class="space-y-2.5">
        <div v-for="job in detail.jobs" :key="job.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <div class="text-sm font-medium text-slate-900">{{ job.pipelineId }}</div>
            <StatusBadge :status="job.status" />
          </div>
          <div class="mt-1 text-xs text-slate-500">{{ formatDateTime(job.createdAt) }}</div>
        </div>
      </div>
    </PageCard>

    <PageCard v-else title="质检结果">
      <div class="space-y-2.5">
        <div v-for="report in detail.reports" :key="report.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <div class="text-sm font-medium text-slate-900">报告 #{{ report.id }}</div>
            <StatusBadge :status="report.qcStatus" />
          </div>
          <div class="mt-1 text-xs text-slate-500">{{ report.summary }}</div>
        </div>
      </div>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchSessionDetail } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import EntitySummaryCard from "@/components/EntitySummaryCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SectionTabs from "@/components/SectionTabs.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { SessionDetailViewModel } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const route = useRoute();
const detail = ref<SessionDetailViewModel | null>(null);
const activeTab = ref("assets");
const tabs = [
  { label: "数据", value: "assets" },
  { label: "处理", value: "jobs" },
  { label: "质检", value: "qc" }
];

const inputAssets = computed(() => detail.value?.assets.filter((asset) => asset.sourceType !== "derived") ?? []);
const derivedAssets = computed(() => detail.value?.assets.filter((asset) => asset.sourceType === "derived") ?? []);
const headerMeta = computed(() => detail.value ? [{ label: "taskId", value: detail.value.session.taskId }, { label: "sessionId", value: detail.value.session.sessionId }] : []);

onMounted(async () => {
  detail.value = await fetchSessionDetail(String(route.params.sessionId));
});
</script>
