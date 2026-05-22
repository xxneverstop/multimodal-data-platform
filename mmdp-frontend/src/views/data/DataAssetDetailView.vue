<template>
  <div v-if="detail" class="space-y-4">
    <PageHeader eyebrow="功能 / 数据" :title="detail.asset.assetName" description="查看资产详情、元数据、处理、质检和关联信息。" :meta="headerMeta">
      <template #actions>
        <BaseButton :to="`/sessions/${detail.asset.sessionId}`">查看 Session</BaseButton>
        <BaseButton variant="primary" :to="`/export?taskId=${detail.asset.taskId}&sessionId=${detail.asset.sessionId}`">查看导出</BaseButton>
      </template>
    </PageHeader>

    <SectionTabs v-model="activeTab" :items="tabs" />

    <PageCard v-if="activeTab === 'overview'" title="基础信息">
      <div class="grid gap-4 xl:grid-cols-2">
        <div class="grid gap-3 md:grid-cols-2">
          <EntitySummaryCard label="taskId" :value="detail.asset.taskId" />
          <EntitySummaryCard label="sessionId" :value="detail.asset.sessionId" />
          <EntitySummaryCard label="assetType" :value="detail.asset.assetType" />
          <EntitySummaryCard label="来源" :value="formatSourceType(detail.asset.sourceType)" />
        </div>
        <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-4 py-4">
          <div class="text-sm font-medium text-slate-900">预览</div>
          <div class="mt-3 rounded-[10px] border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-400">{{ detail.asset.fileName }}</div>
        </div>
      </div>
    </PageCard>

    <PageCard v-else-if="activeTab === 'metadata'" title="元数据">
      <div class="grid gap-3 md:grid-cols-2">
        <EntitySummaryCard v-for="item in detail.metadata" :key="item.label" :label="item.label" :value="item.value" />
      </div>
    </PageCard>

    <PageCard v-else-if="activeTab === 'process'" title="处理记录">
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

    <PageCard v-else-if="activeTab === 'qc'" title="质检结果">
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

    <PageCard v-else title="关联资产">
      <div class="space-y-2.5">
        <div v-for="asset in detail.relatedAssets" :key="asset.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${asset.taskId}`">{{ asset.assetName }}</BaseButton>
            <span class="text-xs text-slate-500">{{ asset.assetType }}</span>
          </div>
        </div>
      </div>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchAssetDetail } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import EntitySummaryCard from "@/components/EntitySummaryCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SectionTabs from "@/components/SectionTabs.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { AssetDetailViewModel } from "@/types/platform";
import { formatDateTime, formatSourceType } from "@/utils/format";

const route = useRoute();
const detail = ref<AssetDetailViewModel | null>(null);
const activeTab = ref("overview");
const tabs = [
  { label: "基础信息", value: "overview" },
  { label: "元数据", value: "metadata" },
  { label: "处理", value: "process" },
  { label: "质检", value: "qc" },
  { label: "关联", value: "lineage" }
];
const headerMeta = computed(() => detail.value ? [{ label: "格式", value: detail.value.asset.fileFormat }, { label: "状态", value: detail.value.asset.qcStatus }] : []);

onMounted(async () => {
  detail.value = await fetchAssetDetail(Number(route.params.assetId), Number(route.query.taskId || 0) || null);
});
</script>
