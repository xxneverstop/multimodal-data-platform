<template>
  <div v-if="detail" class="space-y-4">
    <PageHeader eyebrow="功能 / 采集" :title="detail.task.taskName" description="查看任务下的会话、数据和最近记录。" :meta="headerMeta">
      <template #actions>
        <BaseButton :to="`/sessions/${detail.sessions[0]?.sessionId}`">进入 Session</BaseButton>
        <BaseButton variant="primary" :to="`/upload?taskId=${detail.task.id}&sessionId=${detail.sessions[0]?.sessionId}`">新建</BaseButton>
      </template>
    </PageHeader>

    <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
      <EntitySummaryCard label="taskId" :value="detail.task.id" />
      <EntitySummaryCard label="Session 数" :value="detail.sessions.length" />
      <EntitySummaryCard label="资产数" :value="detail.assets.length" />
      <EntitySummaryCard label="最近质检" :value="detail.reports.length ? detail.reports[0].qcStatus : '暂无'" />
    </div>

    <SectionTabs v-model="activeTab" :items="tabs" />

    <PageCard v-if="activeTab === 'sessions'" title="会话">
      <div class="grid gap-3 md:grid-cols-2">
        <div v-for="session in detail.sessions" :key="session.sessionId" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <div>
              <div class="text-sm font-medium text-slate-900">{{ session.sessionName }}</div>
              <div class="mt-1 text-xs text-slate-500">{{ session.sessionId }}</div>
            </div>
            <StatusBadge :status="session.qcStatus" />
          </div>
        </div>
      </div>
    </PageCard>

    <PageCard v-else-if="activeTab === 'assets'" title="数据">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">资产名称</th>
              <th class="px-3 py-2.5 font-medium">sessionId</th>
              <th class="px-3 py-2.5 font-medium">类型</th>
              <th class="px-3 py-2.5 font-medium">来源</th>
              <th class="px-3 py-2.5 font-medium">状态</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="asset in detail.assets" :key="asset.id">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ asset.assetName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.assetType }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ formatSourceType(asset.sourceType) }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="asset.qcStatus" /></td>
              <td class="px-3 py-2.5 text-right"><BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${detail.task.id}`">详情</BaseButton></td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <PageCard v-else title="最近记录">
      <div class="grid gap-4 xl:grid-cols-2">
        <div class="space-y-2.5">
          <h3 class="text-sm font-medium text-slate-900">处理记录</h3>
          <div v-for="job in detail.jobs" :key="job.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
            <div class="flex items-center justify-between gap-3">
              <div class="text-sm font-medium text-slate-900">{{ job.pipelineId }}</div>
              <StatusBadge :status="job.status" />
            </div>
          </div>
        </div>
        <div class="space-y-2.5">
          <h3 class="text-sm font-medium text-slate-900">质检结果</h3>
          <div v-for="report in detail.reports" :key="report.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
            <div class="flex items-center justify-between gap-3">
              <div class="text-sm font-medium text-slate-900">报告 #{{ report.id }}</div>
              <StatusBadge :status="report.qcStatus" />
            </div>
            <div class="mt-1 text-xs text-slate-500">{{ report.summary }}</div>
          </div>
        </div>
      </div>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchAcquisitionDetail } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import EntitySummaryCard from "@/components/EntitySummaryCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SectionTabs from "@/components/SectionTabs.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { AcquisitionDetailViewModel } from "@/types/platform";
import { formatSourceType } from "@/utils/format";

const route = useRoute();
const detail = ref<AcquisitionDetailViewModel | null>(null);
const activeTab = ref("sessions");
const tabs = [
  { label: "会话", value: "sessions" },
  { label: "数据", value: "assets" },
  { label: "记录", value: "logs" }
];
const headerMeta = computed(() => detail.value ? [{ label: "subjectCode", value: detail.value.task.subjectCode }, { label: "actionName", value: detail.value.task.actionName }] : []);

onMounted(async () => {
  detail.value = await fetchAcquisitionDetail(Number(route.params.taskId));
});
</script>
