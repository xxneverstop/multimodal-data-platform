<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 导出" title="导出" description="按采集会话查看可下载的导出内容。" />

    <PageCard title="筛选条件">
      <div class="space-y-3">
        <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1" />
        <QuickFilterChips v-model="quickFilter" :items="quickItems" />
      </div>
    </PageCard>

    <PageCard title="导出列表">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">采集编号</th>
              <th class="px-3 py-2.5 font-medium">所属任务</th>
              <th class="px-3 py-2.5 font-medium">被试 / 动作</th>
              <th class="px-3 py-2.5 font-medium">数据数</th>
              <th class="px-3 py-2.5 font-medium">总大小</th>
              <th class="px-3 py-2.5 font-medium">质检</th>
              <th class="px-3 py-2.5 font-medium">导出</th>
              <th class="px-3 py-2.5 font-medium">更新时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="session in filteredSessions" :key="session.sessionId" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ session.sessionCode || session.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ session.taskName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ session.subjectCode }} / {{ session.actionName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ session.assetCount }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ formatFileSize(session.totalSize) }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="session.qcStatus" /></td>
              <td class="px-3 py-2.5"><StatusBadge :status="session.exportStatus" /></td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(session.updatedAt) }}</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex justify-end gap-2">
                  <BaseButton size="sm" variant="ghost" :to="`/sessions/${session.sessionId}`">详情</BaseButton>
                  <BaseButton size="sm" variant="ghost" @click="selectedSession = session">下载</BaseButton>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredSessions.length">
              <td colspan="9" class="px-3 py-8 text-center text-slate-400">暂无可导出的采集会话。</td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <AppDrawer :open="Boolean(selectedSession)" title="导出详情" @close="selectedSession = null">
      <div v-if="selectedSession" class="space-y-3">
        <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
          当前导出仍基于会话下已存在的平台文件，尚未生成独立导出包。
        </div>
        <a
          v-for="asset in downloadableAssets"
          :key="asset.id"
          :href="asset.rawAsset.storageUrl || '#'"
          target="_blank"
          rel="noreferrer"
          class="flex items-center justify-between rounded-[10px] border border-slate-200 bg-white px-3 py-3 text-sm text-slate-700 transition hover:bg-slate-50"
        >
          <span class="truncate">{{ asset.assetName }}</span>
          <span class="text-xs text-slate-400">{{ asset.fileFormat }}</span>
        </a>
        <div v-if="!downloadableAssets.length" class="py-8 text-center text-sm text-slate-400">当前采集没有可下载文件。</div>
      </div>
    </AppDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchFinalAssets } from "@/api/platform";
import AppDrawer from "@/components/AppDrawer.vue";
import BaseButton from "@/components/BaseButton.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import QuickFilterChips from "@/components/QuickFilterChips.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { FinalAssetRecord } from "@/types/platform";
import { formatDateTime, formatFileSize } from "@/utils/format";

const sessions = ref<FinalAssetRecord[]>([]);
const selectedSession = ref<FinalAssetRecord | null>(null);
const searchCount = ref(0);
const quickFilter = ref("");
const filters = reactive({ taskId: "", sessionId: "" });

const filterFields: FilterField[] = [
  { key: "taskId", label: "任务编号", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "采集编号", placeholder: "请输入采集编号" },
];

const quickItems = [
  { label: "已完成 QC", value: "qc" },
  { label: "可导出", value: "export" },
];

const filteredSessions = computed(() =>
  sessions.value.filter((session) => {
    const matchTask = !filters.taskId || String(session.taskId).includes(filters.taskId.trim());
    const matchSession =
      !filters.sessionId ||
      session.sessionId.includes(filters.sessionId.trim()) ||
      String(session.sessionCode || "").includes(filters.sessionId.trim());
    if (!matchTask || !matchSession) {
      return false;
    }
    if (quickFilter.value === "qc") {
      return session.qcStatus === "PASSED" || session.qcStatus === "QC_PASSED";
    }
    if (quickFilter.value === "export") {
      return session.exportStatus === "READY";
    }
    return true;
  }),
);

const downloadableAssets = computed(() => selectedSession.value?.assets.filter((asset) => asset.rawAsset.storageUrl) ?? []);

onMounted(async () => {
  sessions.value = await fetchFinalAssets();
});
</script>
