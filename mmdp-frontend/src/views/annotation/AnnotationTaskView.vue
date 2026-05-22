<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 标注" title="标注" description="查看标注任务并跳转到标注页面。" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1">
        <template #actions>
          <BaseButton variant="primary" size="md" @click="dialogOpen = true">
            <BaseIcon name="plus" size="sm" />
            新建
          </BaseButton>
        </template>
      </SearchActionBar>
    </PageCard>

    <PageCard title="标注任务">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">任务名称</th>
              <th class="px-3 py-2.5 font-medium">taskId</th>
              <th class="px-3 py-2.5 font-medium">sessionId</th>
              <th class="px-3 py-2.5 font-medium">关联资产</th>
              <th class="px-3 py-2.5 font-medium">状态</th>
              <th class="px-3 py-2.5 font-medium">标签</th>
              <th class="px-3 py-2.5 font-medium">更新时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="item in filteredTasks" :key="item.id" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ item.name }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.taskId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.assetName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.annotationStatus }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.annotationTag }}</td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(item.updatedAt) }}</td>
              <td class="px-3 py-2.5 text-right"><ExternalEntryButton :href="item.entryUrl" /></td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <AppDialog :open="dialogOpen" title="新建标注任务" description="先保留轻量字段。" @close="dialogOpen = false" @confirm="submitMock('标注任务已创建')">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">任务名称</span>
          <input v-model="dialogForm.name" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">标签</span>
          <select v-model="dialogForm.tag" class="app-input app-input-compact">
            <option value="有效">有效</option>
            <option value="无效">无效</option>
            <option value="异常动作">异常动作</option>
            <option value="时间漂移">时间漂移</option>
          </select>
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm text-slate-500">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchAnnotationTasks } from "@/api/platform";
import AppDialog from "@/components/AppDialog.vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import ExternalEntryButton from "@/components/ExternalEntryButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { AnnotationTaskRecord } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const tasks = ref<AnnotationTaskRecord[]>([]);
const dialogOpen = ref(false);
const dialogMessage = ref("");
const searchCount = ref(0);
const filters = reactive({ taskId: "", sessionId: "" });
const dialogForm = reactive({ name: "", tag: "有效" });
const filterFields: FilterField[] = [
  { key: "taskId", label: "taskId", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "sessionId", placeholder: "请输入会话编号" }
];
const filteredTasks = computed(() => tasks.value.filter((task) => (!filters.taskId || String(task.taskId).includes(filters.taskId.trim())) && (!filters.sessionId || task.sessionId.includes(filters.sessionId.trim()))));

function submitMock(message: string) {
  dialogMessage.value = message;
  window.setTimeout(() => {
    dialogOpen.value = false;
    dialogMessage.value = "";
  }, 600);
}

onMounted(async () => {
  tasks.value = await fetchAnnotationTasks();
});
</script>
