<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 处理" title="处理" description="查看处理规则和执行记录。" />

    <SectionTabs v-model="activeTab" :items="tabs" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1">
        <template #actions>
          <BaseButton v-if="activeTab === 'templates'" variant="primary" size="md" @click="dialogOpen = true">
            <BaseIcon name="plus" size="sm" />
            新建
          </BaseButton>
        </template>
      </SearchActionBar>
    </PageCard>

    <PageCard v-if="activeTab === 'templates'" title="处理规则">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">名称</th>
              <th class="px-3 py-2.5 font-medium">类型</th>
              <th class="px-3 py-2.5 font-medium">输入模态</th>
              <th class="px-3 py-2.5 font-medium">输出类型</th>
              <th class="px-3 py-2.5 font-medium">适用范围</th>
              <th class="px-3 py-2.5 font-medium">最近执行</th>
              <th class="px-3 py-2.5 font-medium">状态</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="template in filteredTemplates" :key="template.id" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ template.name }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ template.templateType }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ template.inputModality }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ template.outputAssetType }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ template.scope }}</td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(template.recentRunAt || undefined) }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="template.recentRunStatus || 'PENDING'" /></td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex justify-end gap-2">
                  <BaseButton size="sm" variant="ghost" @click="selectedTemplate = template">详情</BaseButton>
                  <BaseButton size="sm" variant="ghost" :to="`/upload?taskId=${template.taskId || ''}&sessionId=${template.sessionId || ''}`">执行</BaseButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <PageCard v-else title="执行记录">
      <div class="space-y-2.5">
        <div v-for="job in recentJobs" :key="job.id" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <div class="text-sm font-medium text-slate-900">{{ job.pipelineId }}</div>
            <StatusBadge :status="job.status" />
          </div>
          <div class="mt-1 text-xs text-slate-500">{{ formatDateTime(job.createdAt) }}</div>
        </div>
      </div>
    </PageCard>

    <AppDrawer :open="Boolean(selectedTemplate)" title="处理规则详情" @close="selectedTemplate = null">
      <div v-if="selectedTemplate" class="space-y-3 text-sm text-slate-600">
        <div><span class="font-medium text-slate-900">名称：</span>{{ selectedTemplate.name }}</div>
        <div><span class="font-medium text-slate-900">类型：</span>{{ selectedTemplate.templateType }}</div>
        <div><span class="font-medium text-slate-900">输入模态：</span>{{ selectedTemplate.inputModality }}</div>
        <div><span class="font-medium text-slate-900">输出类型：</span>{{ selectedTemplate.outputAssetType }}</div>
      </div>
    </AppDrawer>

    <AppDialog :open="dialogOpen" title="新建处理规则" description="先保留前端占位字段。" @close="dialogOpen = false" @confirm="submitMock('处理规则已创建')">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">规则名称</span>
          <input v-model="ruleForm.name" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">处理类型</span>
          <input v-model="ruleForm.type" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm text-slate-500">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchPlatformOverview, fetchProcessingTemplates } from "@/api/platform";
import AppDialog from "@/components/AppDialog.vue";
import AppDrawer from "@/components/AppDrawer.vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import SectionTabs from "@/components/SectionTabs.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { ProcessingTemplateRecord } from "@/types/platform";
import type { ProcessingJobResponse } from "@/types/processing";
import { formatDateTime } from "@/utils/format";

const templates = ref<ProcessingTemplateRecord[]>([]);
const recentJobs = ref<ProcessingJobResponse[]>([]);
const selectedTemplate = ref<ProcessingTemplateRecord | null>(null);
const dialogOpen = ref(false);
const dialogMessage = ref("");
const searchCount = ref(0);
const activeTab = ref("templates");
const tabs = [
  { label: "处理规则", value: "templates" },
  { label: "执行记录", value: "jobs" }
];
const filters = reactive({ taskId: "", sessionId: "" });
const ruleForm = reactive({ name: "", type: "" });
const filterFields: FilterField[] = [
  { key: "taskId", label: "taskId", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "sessionId", placeholder: "请输入会话编号" }
];
const filteredTemplates = computed(() => templates.value.filter((template) => (!filters.taskId || String(template.taskId || "").includes(filters.taskId.trim())) && (!filters.sessionId || String(template.sessionId || "").includes(filters.sessionId.trim()))));

function submitMock(message: string) {
  dialogMessage.value = message;
  window.setTimeout(() => {
    dialogOpen.value = false;
    dialogMessage.value = "";
  }, 600);
}

onMounted(async () => {
  templates.value = await fetchProcessingTemplates();
  const overview = await fetchPlatformOverview();
  recentJobs.value = overview.recentJobs;
});
</script>
