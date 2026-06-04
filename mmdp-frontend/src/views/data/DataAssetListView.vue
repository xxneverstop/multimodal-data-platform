<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 数据" title="数据资产" description="查看和筛选当前平台中的数据资产。" :meta="headerMeta" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1">
        <template #actions>
          <BaseButton variant="primary" size="md" :to="uploadEntry">
            <BaseIcon name="plus" size="sm" />
            新建
          </BaseButton>
        </template>
      </SearchActionBar>
    </PageCard>

    <PageCard title="资产列表">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-[var(--color-surface-page)] text-xs text-[var(--color-text-tertiary)]">
            <tr>
              <th class="px-3 py-2 font-medium">资产名称</th>
              <th class="px-3 py-2 font-medium">文件名</th>
              <th class="px-3 py-2 font-medium">文件大小</th>
              <th class="px-3 py-2 font-medium">时长</th>
              <th class="px-3 py-2 font-medium">taskId</th>
              <th class="px-3 py-2 font-medium">sessionId</th>
              <th class="px-3 py-2 font-medium">上传者</th>
              <th class="px-3 py-2 font-medium">上传时间</th>
              <th class="px-3 py-2 font-medium">类型</th>
              <th class="px-3 py-2 font-medium">模态</th>
              <th class="px-3 py-2 font-medium">格式</th>
              <th class="px-3 py-2 font-medium">来源</th>
              <th class="px-3 py-2 font-medium">标注状态</th>
              <th class="px-3 py-2 font-medium">质检状态</th>
              <th class="px-3 py-2 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-[var(--color-border-default)] bg-white">
            <tr v-for="asset in filteredAssets" :key="asset.id" class="hover:bg-[var(--color-brand-50)]/15">
              <td class="px-3 py-2.5 font-medium text-[var(--color-text-primary)] text-[13px]">
                <span class="block max-w-[180px] truncate" :title="asset.assetName">{{ asset.assetName }}</span>
              </td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">
                <span class="block max-w-[160px] truncate" :title="asset.fileName">{{ asset.fileName }}</span>
              </td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ formatFileSize(asset.fileSize) }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.duration }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.taskId }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.sessionId }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.uploader }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-tertiary)] text-[13px]">{{ formatDateTime(asset.uploadedAt) }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.assetType }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.modality }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.fileFormat }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ formatSourceType(asset.sourceType) }}</td>
              <td class="px-3 py-2.5 text-[var(--color-text-secondary)] text-[13px]">{{ asset.annotationStatus }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="asset.qcStatus" /></td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex justify-end gap-1.5">
                  <BaseButton size="sm" variant="ghost" :to="`/data/${asset.id}?taskId=${asset.taskId}`">详情</BaseButton>
                  <BaseButton size="sm" variant="ghost" :to="`/play/${asset.sessionId}`">
                    <BaseIcon name="play" size="sm" />
                    播放
                  </BaseButton>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredAssets.length">
              <td colspan="15" class="px-3 py-10 text-center text-[var(--color-text-tertiary)]">
                <div class="flex flex-col items-center gap-1.5">
                  <BaseIcon name="info" class="text-[var(--color-text-tertiary)] opacity-40" />
                  <span class="text-[13px]">暂无数据资产</span>
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
import { useRoute } from "vue-router";
import { fetchAssetCatalog } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { AssetListItem } from "@/types/platform";
import { formatDateTime, formatFileSize, formatSourceType } from "@/utils/format";

const route = useRoute();
const assets = ref<AssetListItem[]>([]);
const searchCount = ref(0);
const filters = reactive({
  taskId: String(route.query.taskId ?? ""),
  sessionId: String(route.query.sessionId ?? ""),
  sourceType: "",
  keyword: ""
});

const filterFields: FilterField[] = [
  { key: "taskId", label: "taskId", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "sessionId", placeholder: "请输入会话编号" },
  { key: "sourceType", label: "来源", type: "select", options: [{ label: "手动上传", value: "upload" }, { label: "采集同步", value: "acquisition_sync" }, { label: "处理派生", value: "derived" }] },
  { key: "keyword", label: "关键词", placeholder: "请输入资产名称或文件名" }
];

const filteredAssets = computed(() =>
  assets.value.filter((asset) => {
    const matchTask = !filters.taskId || String(asset.taskId).includes(filters.taskId.trim());
    const matchSession = !filters.sessionId || asset.sessionId.includes(filters.sessionId.trim());
    const matchSource = !filters.sourceType || asset.sourceType === filters.sourceType;
    const keyword = filters.keyword.trim().toLowerCase();
    const matchKeyword = !keyword || [asset.assetName, asset.fileName].some((value) => value.toLowerCase().includes(keyword));
    return matchTask && matchSession && matchSource && matchKeyword;
  })
);

const uploadEntry = computed(() => `/upload?taskId=${filters.taskId || ""}&sessionId=${filters.sessionId || ""}`);
const headerMeta = computed(() => [
  { label: "总数", value: assets.value.length },
  { label: "结果", value: filteredAssets.value.length }
]);

onMounted(async () => {
  assets.value = await fetchAssetCatalog();
});
</script>
