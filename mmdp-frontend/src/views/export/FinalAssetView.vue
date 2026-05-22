<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 导出" title="导出" description="查看可交付的成品资产。" />

    <PageCard title="筛选条件">
      <div class="space-y-3">
        <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1" />
        <QuickFilterChips v-model="quickFilter" :items="quickItems" />
      </div>
    </PageCard>

    <PageCard title="成品资产">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">资产名称</th>
              <th class="px-3 py-2.5 font-medium">taskId</th>
              <th class="px-3 py-2.5 font-medium">sessionId</th>
              <th class="px-3 py-2.5 font-medium">类型</th>
              <th class="px-3 py-2.5 font-medium">模态</th>
              <th class="px-3 py-2.5 font-medium">格式</th>
              <th class="px-3 py-2.5 font-medium">处理状态</th>
              <th class="px-3 py-2.5 font-medium">标注状态</th>
              <th class="px-3 py-2.5 font-medium">质检状态</th>
              <th class="px-3 py-2.5 font-medium">可交付</th>
              <th class="px-3 py-2.5 font-medium">更新时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="asset in filteredAssets" :key="asset.id" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ asset.assetName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.taskId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.assetType }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.modality }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.fileFormat }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="asset.processingStatus" /></td>
              <td class="px-3 py-2.5 text-slate-600">{{ asset.annotationStatus }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="asset.qcStatus" /></td>
              <td class="px-3 py-2.5"><StatusBadge :status="asset.deliverableStatus" /></td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(asset.updatedAt) }}</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex justify-end gap-2">
                  <BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${asset.taskId}`">详情</BaseButton>
                  <BaseButton size="sm" variant="ghost" href="#">
                    <BaseIcon name="download" size="sm" />
                    下载
                  </BaseButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchFinalAssets } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import QuickFilterChips from "@/components/QuickFilterChips.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { FinalAssetRecord } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const assets = ref<FinalAssetRecord[]>([]);
const searchCount = ref(0);
const quickFilter = ref("");
const filters = reactive({ taskId: "", sessionId: "" });
const filterFields: FilterField[] = [
  { key: "taskId", label: "taskId", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "sessionId", placeholder: "请输入会话编号" }
];
const quickItems = [
  { label: "已完成 QC", value: "qc" },
  { label: "已完成标注", value: "annotation" },
  { label: "已完成处理", value: "processing" }
];
const filteredAssets = computed(() =>
  assets.value.filter((asset) => {
    const matchTask = !filters.taskId || String(asset.taskId).includes(filters.taskId.trim());
    const matchSession = !filters.sessionId || asset.sessionId.includes(filters.sessionId.trim());
    if (!matchTask || !matchSession) {
      return false;
    }
    if (quickFilter.value === "qc") {
      return asset.qcStatus === "PASSED" || asset.qcStatus === "QC_PASSED";
    }
    if (quickFilter.value === "annotation") {
      return asset.annotationStatus === "已标注";
    }
    if (quickFilter.value === "processing") {
      return asset.processingStatus === "SUCCESS";
    }
    return true;
  })
);

onMounted(async () => {
  assets.value = await fetchFinalAssets();
});
</script>
